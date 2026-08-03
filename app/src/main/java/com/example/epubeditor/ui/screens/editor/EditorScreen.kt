package com.example.epubeditor.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.epubeditor.R
import com.example.epubeditor.ui.components.LoadingOverlay
import com.example.epubeditor.util.sanitizeFileName
import com.example.epubeditor.ui.screens.editor.tabs.AssetManagerTab
import com.example.epubeditor.ui.screens.editor.tabs.HtmlEditorTab
import com.example.epubeditor.ui.screens.editor.tabs.MetadataEditorTab
import com.example.epubeditor.ui.screens.editor.tabs.TocEditorTab

@Composable
private fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    minFontSize: TextUnit = 12.sp,
    style: TextStyle = MaterialTheme.typography.titleLarge
) {
    var currentStyle by remember(text, style) { mutableStateOf(style) }
    var shouldDraw by remember(text) { mutableStateOf(false) }

    Text(
            text = text,
            modifier = modifier.drawWithContent { if (shouldDraw) drawContent() },
            style = currentStyle.copy(lineHeight = (currentStyle.fontSize.value * 1.1f).sp),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && currentStyle.fontSize > minFontSize) {
                currentStyle = currentStyle.copy(fontSize = (currentStyle.fontSize.value * 0.9f).sp)
            } else {
                shouldDraw = true
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val book = viewModel.book
    var showSaveSuccess by remember { mutableStateOf(false) }
    var saveMenuExpanded by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }

    val showSuccess = uiState.lastSavedFile != null || uiState.lastExportedUri != null
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            showSaveSuccess = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutoSizeText(
                        text = book?.opf?.metadata?.title ?: stringResource(R.string.app_name),
                        maxLines = 2
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.hasUnsavedChanges) {
                            showExitConfirm = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = uiState.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo_cd))
                    }
                    IconButton(onClick = viewModel::redo, enabled = uiState.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.editor_redo_cd))
                    }
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        IconButton(onClick = { saveMenuExpanded = true }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.editor_save_cd))
                        }
                        DropdownMenu(
                            expanded = saveMenuExpanded,
                            onDismissRequest = { saveMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_save_all)) },
                                onClick = {
                                    saveMenuExpanded = false
                                    viewModel.saveCurrentState()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_export)) },
                                onClick = {
                                    saveMenuExpanded = false
                                    viewModel.saveBook()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_export_to_folder)) },
                                onClick = {
                                    saveMenuExpanded = false
                                    val defaultName = book?.opf?.metadata?.title
                                        ?.takeIf { it.isNotBlank() }
                                        ?.sanitizeFileName()
                                        ?.plus(".epub")
                                        ?: "book.epub"
                                    exportLauncher.launch(defaultName)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.selectedTab == EditorTab.TEXT,
                    onClick = { viewModel.selectTab(EditorTab.TEXT) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text(stringResource(R.string.editor_text)) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == EditorTab.TOC,
                    onClick = { viewModel.selectTab(EditorTab.TOC) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.editor_toc)) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == EditorTab.METADATA,
                    onClick = { viewModel.selectTab(EditorTab.METADATA) },
                    icon = { Icon(Icons.Default.Book, contentDescription = null) },
                    label = { Text(stringResource(R.string.editor_metadata)) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == EditorTab.ASSETS,
                    onClick = { viewModel.selectTab(EditorTab.ASSETS) },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    label = { Text(stringResource(R.string.editor_assets)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (book == null) {
                Text(
                    text = stringResource(R.string.editor_no_book),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    when (uiState.selectedTab) {
                        EditorTab.TEXT -> HtmlEditorTab(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        EditorTab.TOC -> TocEditorTab(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        EditorTab.METADATA -> MetadataEditorTab(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        EditorTab.ASSETS -> AssetManagerTab(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(uiState.error ?: "") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onBack()
                }) {
                    Text(stringResource(R.string.editor_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSaveSuccess) {
        AlertDialog(
            onDismissRequest = { showSaveSuccess = false },
            title = { Text(stringResource(R.string.editor_saved_title)) },
            text = {
                val message = when {
                    uiState.lastExportedUri != null -> stringResource(
                        R.string.editor_saved_exported_message,
                        uiState.lastExportedUri.toString()
                    )
                    uiState.lastSavedFile != null -> stringResource(
                        R.string.editor_saved_to_message,
                        uiState.lastSavedFile?.absolutePath ?: ""
                    )
                    else -> stringResource(R.string.editor_saved_message)
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = { showSaveSuccess = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    LoadingOverlay(visible = uiState.isLoading)
}
