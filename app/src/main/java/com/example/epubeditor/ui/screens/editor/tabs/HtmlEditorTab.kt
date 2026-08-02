package com.example.epubeditor.ui.screens.editor.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.epubeditor.R
import com.example.epubeditor.ui.screens.editor.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun HtmlEditorTab(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var cursorOffset by remember { mutableIntStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var replaceCount by remember { mutableStateOf<Int?>(null) }
    viewModel.book ?: return
    val uiState by viewModel.uiState.collectAsState()
    val sourceMode = uiState.sourceMode
    val currentHtml by viewModel.currentChapterHtml.collectAsState()

    fun applyTextChange(transform: (TextFieldValue) -> TextFieldValue) {
        val newValue = transform(textValue)
        textValue = newValue
        cursorOffset = newValue.selection.start
        viewModel.setChapterHtml(newValue.text, autoCommit = false)
        viewModel.commitPendingHtml()
    }

    LaunchedEffect(currentHtml) {
        if (currentHtml != textValue.text) {
            val newCursor = textValue.selection.start.coerceIn(0, currentHtml.length)
            textValue = TextFieldValue(currentHtml, TextRange(newCursor))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.editor_text),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        ChapterSelector(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TooltipIconButton(
                tooltip = stringResource(if (sourceMode) R.string.html_preview_cd else R.string.html_source_cd),
                onClick = { viewModel.setSourceMode(!sourceMode) }
            ) {
                Icon(
                    imageVector = if (sourceMode) Icons.Default.Visibility else Icons.Default.Code,
                    contentDescription = stringResource(R.string.html_toggle_mode_cd)
                )
            }

            if (sourceMode) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.html_bold_cd),
                    onClick = { applyTextChange { wrapSelection(it, "<b>", "</b>") } }
                ) {
                    Icon(Icons.Default.FormatBold, contentDescription = stringResource(R.string.html_bold_cd))
                }
                TooltipIconButton(
                    tooltip = stringResource(R.string.html_italic_cd),
                    onClick = { applyTextChange { wrapSelection(it, "<i>", "</i>") } }
                ) {
                    Icon(Icons.Default.FormatItalic, contentDescription = stringResource(R.string.html_italic_cd))
                }
                TooltipIconButton(
                    tooltip = stringResource(R.string.html_list_cd),
                    onClick = { applyTextChange { wrapSelection(it, "<ul>\n<li>", "</li>\n</ul>") } }
                ) {
                    Icon(Icons.Default.FormatListBulleted, contentDescription = stringResource(R.string.html_list_cd))
                }
                HeadingMenu { tag ->
                    applyTextChange { wrapSelection(it, "<$tag>", "</$tag>") }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TooltipIconButton(
                tooltip = stringResource(R.string.html_select_all_cd),
                onClick = { textValue = textValue.copy(selection = TextRange(0, textValue.text.length)) }
            ) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.html_select_all_cd))
            }
            TooltipIconButton(
                tooltip = stringResource(R.string.html_find_replace_cd),
                onClick = { showFindReplace = true },
                enabled = sourceMode
            ) {
                Icon(Icons.Default.FindReplace, contentDescription = stringResource(R.string.html_find_replace_cd))
            }
            TooltipIconButton(
                tooltip = stringResource(R.string.html_clear_cd),
                onClick = { showClearConfirm = true },
                enabled = sourceMode
            ) {
                Icon(Icons.Default.ClearAll, contentDescription = stringResource(R.string.html_clear_cd))
            }
            TooltipIconButton(
                tooltip = stringResource(R.string.html_split_cd),
                onClick = { viewModel.splitChapterAtCursor(cursorOffset) },
                enabled = sourceMode
            ) {
                Icon(Icons.Default.HorizontalSplit, contentDescription = stringResource(R.string.html_split_cd))
            }
            TooltipIconButton(
                tooltip = stringResource(R.string.html_merge_cd),
                onClick = { viewModel.mergeWithNextChapter() },
                enabled = sourceMode
            ) {
                Icon(Icons.Default.MergeType, contentDescription = stringResource(R.string.html_merge_cd))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                cursorOffset = it.selection.start
                if (sourceMode) {
                    viewModel.setChapterHtml(it.text, autoCommit = true)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            readOnly = !sourceMode,
            textStyle = MaterialTheme.typography.bodyLarge,
            label = { Text(if (sourceMode) stringResource(R.string.html_source) else stringResource(R.string.html_visual)) }
        )

        if (sourceMode) {
            Button(
                onClick = { viewModel.commitPendingHtml() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.html_clear_title)) },
            text = { Text(stringResource(R.string.html_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setChapterHtml("", autoCommit = false)
                    viewModel.commitPendingHtml()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showFindReplace) {
        FindReplaceDialog(
            initialText = textValue.text,
            onDismiss = { showFindReplace = false },
            onApply = { newText, count ->
                viewModel.setChapterHtml(newText, autoCommit = false)
                viewModel.commitPendingHtml()
                replaceCount = count
            }
        )
    }

    replaceCount?.let { count ->
        val message = if (count > 0) {
            stringResource(R.string.html_replaced_count, count)
        } else {
            stringResource(R.string.html_no_matches)
        }
        AlertDialog(
            onDismissRequest = { replaceCount = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { replaceCount = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}

@Composable
private fun ChapterSelector(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val book = viewModel.book ?: return
    val uiState by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val selectedItem = book.opf.manifest.find { it.id == uiState.selectedChapterId }
    val displayText = selectedItem?.href ?: stringResource(R.string.html_select_chapter)

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.html_current_chapter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(displayText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            book.opf.spine.forEach { id ->
                val item = book.opf.manifest.find { it.id == id } ?: return@forEach
                DropdownMenuItem(
                    text = { Text(item.href) },
                    onClick = {
                        viewModel.selectChapter(item.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadingMenu(onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TooltipIconButton(
        tooltip = stringResource(R.string.html_heading_cd),
        onClick = { expanded = true }
    ) {
        Text("H", style = MaterialTheme.typography.titleMedium)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        (1..6).forEach { level ->
            val tag = "h$level"
            DropdownMenuItem(
                text = { Text(tag.uppercase()) },
                onClick = {
                    onSelect(tag)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun FindReplaceDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onApply: (String, Int) -> Unit
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.html_find_replace_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = { Text(stringResource(R.string.html_find_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    label = { Text(stringResource(R.string.html_replace_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (find.isNotEmpty()) {
                        val result = initialText.replace(find, replace)
                        val count = initialText.windowed(find.length).count { it == find }
                        onApply(result, count)
                    }
                    onDismiss()
                },
                enabled = find.isNotEmpty()
            ) {
                Text(stringResource(R.string.html_replace_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun wrapSelection(value: TextFieldValue, before: String, after: String): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + before + selected + after + text.substring(end)
    val newCursor = start + before.length + selected.length + after.length
    return TextFieldValue(newText, TextRange(newCursor))
}
