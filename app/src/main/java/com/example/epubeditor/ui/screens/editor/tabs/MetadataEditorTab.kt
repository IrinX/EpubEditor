package com.example.epubeditor.ui.screens.editor.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.epubeditor.R
import com.example.epubeditor.ui.screens.editor.EditorViewModel

@Composable
fun MetadataEditorTab(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val book = viewModel.book ?: return
    val metadata = book.opf.metadata
    val scrollState = rememberScrollState()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateCoverFromUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.editor_metadata),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        MetadataField(
            label = stringResource(R.string.metadata_title),
            value = metadata.title,
            onValueChange = { viewModel.updateMetadata { m -> m.title = it } }
        )
        MetadataField(
            label = stringResource(R.string.metadata_authors),
            value = metadata.authors.joinToString(", "),
            onValueChange = { viewModel.updateMetadata { m -> m.authors = it.split(",").map(String::trim).toMutableList() } }
        )
        MetadataField(
            label = stringResource(R.string.metadata_publisher),
            value = metadata.publisher,
            onValueChange = { viewModel.updateMetadata { m -> m.publisher = it } }
        )
        MetadataField(
            label = stringResource(R.string.metadata_language),
            value = metadata.language,
            onValueChange = { viewModel.updateMetadata { m -> m.language = it } }
        )
        MetadataField(
            label = stringResource(R.string.metadata_identifier),
            value = metadata.identifier,
            onValueChange = { viewModel.updateMetadata { m -> m.identifier = it } }
        )
        MetadataField(
            label = stringResource(R.string.metadata_date),
            value = metadata.date,
            onValueChange = { viewModel.updateMetadata { m -> m.date = it } }
        )
        OutlinedTextField(
            value = metadata.description,
            onValueChange = { viewModel.updateMetadata { m -> m.description = it } },
            label = { Text(stringResource(R.string.metadata_description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { imageLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Image, contentDescription = stringResource(R.string.metadata_cover_cd))
            Text(stringResource(R.string.metadata_change_cover), modifier = Modifier.padding(start = 8.dp))
        }

        metadata.coverManifestId?.let { coverId ->
            Text(
                text = stringResource(R.string.metadata_cover_id, coverId),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true
    )
}
