package com.example.epubeditor.ui.screens.editor.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import com.example.epubeditor.R
import com.example.epubeditor.data.epub.model.ManifestItem
import com.example.epubeditor.ui.components.ImagePreviewDialog
import com.example.epubeditor.ui.screens.editor.EditorViewModel

@Composable
fun AssetManagerTab(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val book = viewModel.book ?: return
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var renameItem by remember { mutableStateOf<ManifestItem?>(null) }
    var newName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<java.io.File?>(null) }
    var isFabVisible by remember { mutableStateOf(true) }

    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileNameFromUri(context, it) ?: "asset_${System.currentTimeMillis()}"
            viewModel.addAssetFromUri(it, name)
        }
    }

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

    val groups = remember(book.opf.manifest, uiState.bookVersion) { categorizeAssets(book.opf.manifest) }
    val hasAnyAssets = groups.any { it.items.isNotEmpty() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.assets_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!hasAnyAssets) {
                item {
                    Text(
                        text = stringResource(R.string.assets_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                groups.forEach { group ->
                    if (group.items.isNotEmpty()) {
                        item(key = "header_${group.titleRes}") {
                            SectionHeader(
                                title = stringResource(group.titleRes),
                                count = group.items.size
                            )
                        }
                        items(group.items, key = { it.id }) { item ->
                            val file = remember(item.href) { book.resolve(item.href).takeIf { it.exists() } }
                            AssetItem(
                                item = item,
                                file = file,
                                onPreview = { file?.let { previewFile = it } },
                                onRename = {
                                    renameItem = item
                                    newName = item.href.substringAfterLast("/")
                                },
                                onDelete = { viewModel.deleteAsset(item) }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isFabVisible,
            enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FloatingActionButton(
                onClick = { addLauncher.launch("*/*") },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.assets_add)
                )
            }
        }
    }

    if (renameItem != null) {
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text(stringResource(R.string.assets_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.assets_new_name_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameItem?.let { viewModel.renameAsset(it, newName) }
                    renameItem = null
                }) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ImagePreviewDialog(
        imageFile = previewFile,
        onDismiss = { previewFile = null }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun AssetItem(
    item: ManifestItem,
    file: java.io.File?,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val isImage = item.mediaType.startsWith("image/")
    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImage && file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.image_preview_cd),
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp)
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPreview() },
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.href, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = item.mediaType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.assets_rename_cd))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.assets_delete_cd))
            }
        }
    }
}

private data class AssetGroup(
    val titleRes: Int,
    val items: List<ManifestItem>
)

private enum class AssetCategory {
    METADATA, TEXT, IMAGES, STYLES, OTHER
}

private fun categorizeAssets(manifest: List<ManifestItem>): List<AssetGroup> {
    val metadata = mutableListOf<ManifestItem>()
    val text = mutableListOf<ManifestItem>()
    val images = mutableListOf<ManifestItem>()
    val styles = mutableListOf<ManifestItem>()
    val other = mutableListOf<ManifestItem>()

    manifest.forEach { item ->
        when (classifyAsset(item.href)) {
            AssetCategory.METADATA -> metadata.add(item)
            AssetCategory.TEXT -> text.add(item)
            AssetCategory.IMAGES -> images.add(item)
            AssetCategory.STYLES -> styles.add(item)
            AssetCategory.OTHER -> other.add(item)
        }
    }

    return listOf(
        AssetGroup(R.string.assets_metadata, metadata),
        AssetGroup(R.string.assets_text, text),
        AssetGroup(R.string.assets_images, images),
        AssetGroup(R.string.assets_styles, styles),
        AssetGroup(R.string.assets_other, other)
    )
}

private fun classifyAsset(href: String): AssetCategory {
    val segments = href.split("/").dropLast(1).map { it.lowercase() }
    return when {
        segments.isEmpty() -> AssetCategory.METADATA
        "text" in segments -> AssetCategory.TEXT
        "images" in segments -> AssetCategory.IMAGES
        "styles" in segments -> AssetCategory.STYLES
        else -> AssetCategory.OTHER
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    return if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    } else {
        uri.lastPathSegment
    }
}
