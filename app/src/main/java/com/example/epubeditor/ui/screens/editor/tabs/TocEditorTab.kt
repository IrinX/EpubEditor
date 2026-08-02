package com.example.epubeditor.ui.screens.editor.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val toc = book.toc
    var newLabel by remember { mutableStateOf("") }
    var newSrc by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.toc_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        DraggableTocList(
            points = toc.rootPoints,
            onMove = { from, to -> viewModel.moveTocNode(from, to) },
            onItemClick = { point ->
                val href = point.src.substringBefore("#")
                val item = book.opf.manifest.find { it.href == href }
                item?.let { viewModel.selectChapter(it.id) }
            },
            onDelete = { point -> viewModel.removeTocNode(point.id) },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            label = { Text(stringResource(R.string.toc_new_title_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newSrc,
            onValueChange = { newSrc = it },
            label = { Text(stringResource(R.string.toc_href_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (newLabel.isNotBlank() && newSrc.isNotBlank()) {
                    viewModel.addTocNode(null, newLabel, newSrc)
                    newLabel = ""
                    newSrc = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.toc_add))
            Text(stringResource(R.string.toc_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
