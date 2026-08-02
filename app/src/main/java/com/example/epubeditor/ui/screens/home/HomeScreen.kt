package com.example.epubeditor.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.epubeditor.R
import com.example.epubeditor.data.epub.model.EpubBook
import com.example.epubeditor.data.repository.RecentBook
import com.example.epubeditor.ui.components.LoadingOverlay
import com.example.epubeditor.ui.screens.settings.SettingsScreen
import com.example.epubeditor.ui.screens.settings.SettingsViewModel
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
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.openFromUri(it) }
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
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.home_recent)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.home_open)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.home_settings)) }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(onClick = { openLauncher.launch("application/epub+zip") }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_open_epub_cd))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> RecentTab(
                    recentBooks = uiState.recentBooks,
                    selectionMode = uiState.selectionMode,
                    selectedIds = uiState.selectedIds,
                    onOpenRecent = viewModel::openRecent,
                    onSetSelectionMode = viewModel::setSelectionMode,
                    onToggleSelection = viewModel::toggleSelection,
                    onSelectAll = viewModel::selectAll,
                    onDeleteSelected = viewModel::deleteSelectedBooks
                )
                1 -> OpenTab(onOpenClick = { openLauncher.launch("application/epub+zip") })
                2 -> SettingsScreen(viewModel = settingsViewModel)
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

    LoadingOverlay(visible = uiState.isLoading)
}

@Composable
private fun RecentTab(
    recentBooks: List<RecentBook>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onOpenRecent: (RecentBook) -> Unit,
    onSetSelectionMode: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (recentBooks.isNotEmpty()) {
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

        if (recentBooks.isEmpty()) {
            Text(
                text = stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                items(recentBooks, key = { it.id }) { book ->
                    RecentBookItem(
                        book = book,
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(book.id),
                        onClick = {
                            if (selectionMode) {
                                onToggleSelection(book.id)
                            } else {
                                onOpenRecent(book)
                            }
                        },
                        onToggleSelection = { onToggleSelection(book.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentBookItem(
    book: RecentBook,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val coverFile = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { java.io.File(it).takeIf { f -> f.exists() } }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() }
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

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
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRecentDate(book.openedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatRecentDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(timestamp))
}

@Composable
private fun OpenTab(onOpenClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.home_open_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onOpenClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.home_open_button))
        }
    }
}
