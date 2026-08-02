package com.example.epubeditor.ui.screens.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.epubeditor.R
import com.example.epubeditor.data.epub.model.NavPoint

@Composable
@Suppress("UNUSED_PARAMETER")
fun DraggableTocList(
    points: List<NavPoint>,
    onMove: (Int, Int) -> Unit,
    onItemClick: (NavPoint) -> Unit,
    onDelete: (NavPoint) -> Unit,
    modifier: Modifier = Modifier,
    level: Int = 0
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(points, key = { _, item -> item.id }) { _, point ->
            TocItemCard(
                point = point,
                level = level,
                onClick = { onItemClick(point) },
                onDelete = { onDelete(point) },
                dragHandle = {
                    IconButton(onClick = { }, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.toc_drag_cd))
                    }
                }
            )
            if (point.children.isNotEmpty()) {
                DraggableTocList(
                    points = point.children,
                    onMove = { _, _ -> },
                    onItemClick = onItemClick,
                    onDelete = onDelete,
                    level = level + 1
                )
            }
        }
    }
}

@Composable
private fun TocItemCard(
    point: NavPoint,
    level: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (8 + level * 16).dp, top = 4.dp, end = 8.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            dragHandle()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.label.ifBlank { stringResource(R.string.toc_untitled) },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = point.src,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.toc_delete_cd)
                )
            }
        }
    }
}
