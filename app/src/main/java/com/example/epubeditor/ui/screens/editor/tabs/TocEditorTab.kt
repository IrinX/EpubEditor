package com.example.epubeditor.ui.screens.editor.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.epubeditor.R
import com.example.epubeditor.ui.screens.editor.EditorViewModel
import com.example.epubeditor.ui.screens.editor.components.DraggableTocList

@Composable
fun TocEditorTab(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val book = viewModel.book ?: return
    val uiState by viewModel.uiState.collectAsState()
    val toc = book.toc
    var showAddDialog by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var selectedHref by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isFabVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30) {
                    isFabVisible = false
                } else if (available.y > 30) {
                    isFabVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val tocPoints = remember(uiState.bookVersion) { toc.rootPoints }
    val textFiles = remember(uiState.bookVersion) {
        book.opf.manifest.filter { it.href.startsWith("text/", ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.toc_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            DraggableTocList(
                points = tocPoints,
                onMove = { from, to -> viewModel.moveTocNode(from, to) },
                onItemClick = { point ->
                    val href = point.src.substringBefore("#")
                    val item = book.opf.manifest.find { it.href == href }
                    item?.let { viewModel.selectChapter(it.id) }
                },
                onDelete = { point -> viewModel.removeTocNode(point.id) },
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            )
        }

        AnimatedVisibility(
            visible = isFabVisible,
            enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FloatingActionButton(
                onClick = {
                    newLabel = ""
                    selectedHref = textFiles.firstOrNull()?.href
                    showAddDialog = true
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.toc_add_fab_cd)
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.toc_add_dialog_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text(stringResource(R.string.toc_new_title_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.toc_select_file),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        TextButton(onClick = { dropdownExpanded = true }) {
                            Text(
                                text = selectedHref
                                    ?: stringResource(R.string.toc_select_file)
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            if (textFiles.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.toc_no_text_files)) },
                                    onClick = { dropdownExpanded = false }
                                )
                            } else {
                                textFiles.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.href) },
                                        onClick = {
                                            selectedHref = item.href
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val href = selectedHref
                        if (newLabel.isNotBlank() && href != null) {
                            viewModel.addTocNode(null, newLabel, href)
                            showAddDialog = false
                            newLabel = ""
                            selectedHref = null
                        }
                    },
                    enabled = newLabel.isNotBlank() && selectedHref != null
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
