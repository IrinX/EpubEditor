package com.example.epubeditor.ui.screens.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.epubeditor.R
import com.example.epubeditor.data.epub.model.EpubBook
import com.example.epubeditor.ui.components.LoadingOverlay
import com.example.epubeditor.ui.screens.home.DirectoryBook
import com.example.epubeditor.ui.screens.settings.SettingsScreen
import com.example.epubeditor.ui.screens.settings.SettingsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenBook: (EpubBook) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isHomeFabVisible by remember { mutableStateOf(true) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromUri(it) }
    }

    val context = LocalContext.current
    var showAllFilesDialog by remember { mutableStateOf(false) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showAllFilesDialog = true
            }
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(permission)
            }
        }
    }

    if (uiState.openedBook != null) {
        onOpenBook(uiState.openedBook!!)
        viewModel.clearOpenedBook()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.home_directory)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.home_settings)) }
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> DirectoryTab(
                        books = uiState.directoryBooks,
                        selectionMode = uiState.selectionMode,
                        selectedIds = uiState.selectedIds,
                        onOpenBook = { book -> viewModel.openFromFile(book.file) },
                        onSetSelectionMode = viewModel::setSelectionMode,
                        onToggleSelection = viewModel::toggleSelection,
                        onSelectAll = viewModel::selectAll,
                        onDeleteSelected = viewModel::deleteSelectedBooks,
                        onFabVisibilityChanged = { isHomeFabVisible = it }
                    )
                    1 -> SettingsScreen(viewModel = settingsViewModel)
                }
            }

            if (selectedTab == 0) {
                AnimatedVisibility(
                    visible = isHomeFabVisible,
                    enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    FloatingActionButton(
                        onClick = { importLauncher.launch("application/epub+zip") },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_import_file))
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

    if (showAllFilesDialog) {
        AlertDialog(
            onDismissRequest = { showAllFilesDialog = false },
            title = { Text(stringResource(R.string.all_files_permission_title)) },
            text = { Text(stringResource(R.string.all_files_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                    showAllFilesDialog = false
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAllFilesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LoadingOverlay(visible = uiState.isLoading)
}

@Composable
private fun DirectoryTab(
    books: List<DirectoryBook>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onOpenBook: (DirectoryBook) -> Unit,
    onSetSelectionMode: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onFabVisibilityChanged: (Boolean) -> Unit = {}
) {
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30) {
                    onFabVisibilityChanged(false)
                } else if (available.y > 30) {
                    onFabVisibilityChanged(true)
                }
                return Offset.Zero
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.home_directory_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (books.isNotEmpty()) {
                if (selectionMode) {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = stringResource(R.string.home_select_all)
                        )
                    }
                    IconButton(
                        onClick = onDeleteSelected,
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                    IconButton(onClick = { onSetSelectionMode(false) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                } else {
                    IconButton(onClick = { onSetSelectionMode(true) }) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = stringResource(R.string.home_select_mode)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (books.isEmpty()) {
            Text(
                text = stringResource(R.string.home_directory_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                items(books, key = { it.file.absolutePath }) { book ->
                    DirectoryBookItem(
                        book = book,
                        selected = selectedIds.contains(book.file.absolutePath),
                        onClick = {
                            if (selectionMode) {
                                onToggleSelection(book.file.absolutePath)
                            } else {
                                onOpenBook(book)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryBookItem(
    book: DirectoryBook,
    selected: Boolean,
    onClick: () -> Unit
) {
    val coverFile = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { File(it).takeIf { f -> f.exists() } }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                CardDefaults.cardColors().containerColor
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            coverFile?.let { file ->
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.metadata_cover_cd),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            } ?: run {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileDate(book.modified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(timestamp))
}
