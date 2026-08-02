package com.example.epubeditor.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubeditor.R
import com.example.epubeditor.data.epub.model.EpubBook
import com.example.epubeditor.data.repository.AppPreferences
import com.example.epubeditor.data.repository.RecentBook
import com.example.epubeditor.data.repository.SettingsRepository
import com.example.epubeditor.data.repository.EpubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: EpubRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(recentBooks = prefs.recentBooks) }
                refreshDirectory()
            }
        }
    }

    fun refreshDirectory() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                runCatching {
                    settingsRepository.getSaveDirectory()
                        .listFiles { file -> file.isFile && file.extension.equals("epub", ignoreCase = true) }
                        ?.sortedByDescending { it.lastModified() }
                        ?: emptyList()
                }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(directoryFiles = files) }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    val saveDir = settingsRepository.getSaveDirectory()
                    var fileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                        ?.let { if (it.endsWith(".epub", ignoreCase = true)) it else "$it.epub" }
                        ?: "imported.epub"
                    val target = uniqueFile(saveDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("无法读取所选文件")
                }
                refreshDirectory()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun uniqueFile(directory: File, name: String): File {
        var file = File(directory, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast(".", name)
        val ext = name.substringAfterLast(".", "")
        var index = 1
        while (file.exists()) {
            val newName = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            file = File(directory, newName)
            index++
        }
        return file
    }

    fun openFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val book = repository.openFromUri(uri)
                settingsRepository.addRecentBook(book, sourceUri = uri, sourceFile = book.sourceFile)
                _uiState.update { it.copy(isLoading = false, openedBook = book) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun openFromFile(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val book = repository.openFromFile(file)
                settingsRepository.addRecentBook(book, sourceFile = file)
                _uiState.update { it.copy(isLoading = false, openedBook = book) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }


    fun openRecent(recent: RecentBook) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val book = when {
                    recent.internalPath.isNotBlank() -> {
                        val file = File(recent.internalPath)
                        if (!file.exists()) throw IllegalArgumentException("文件不存在: ${recent.internalPath}")
                        val originalUri = recent.originalUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                        repository.openFromFile(file, bookId = recent.id, originalUri = originalUri)
                    }
                    recent.uri.startsWith("content://") -> {
                        repository.openFromUri(Uri.parse(recent.uri))
                    }
                    else -> {
                        val path = recent.uri.removePrefix("file://")
                        val file = File(path)
                        if (!file.exists()) throw IllegalArgumentException("文件不存在: ${recent.uri}")
                        repository.openFromFile(file, bookId = recent.id)
                    }
                }
                settingsRepository.addRecentBook(
                    book,
                    sourceUri = book.originalUri,
                    sourceFile = book.sourceFile
                )
                _uiState.update { it.copy(isLoading = false, openedBook = book) }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.home_recent_permission_lost)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearRecentBooks() {
        viewModelScope.launch { settingsRepository.clearRecentBooks() }
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.update { it.copy(selectionMode = enabled, selectedIds = emptySet()) }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val current = state.selectedIds
            state.copy(selectedIds = if (current.contains(id)) current - id else current + id)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.recentBooks.map { it.id }.toSet())
        }
    }

    fun deleteSelectedBooks() {
        val selected = _uiState.value.selectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val remaining = _uiState.value.recentBooks.filter { it.id !in selected }
            settingsRepository.saveRecentBooks(remaining)
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
        }
    }

    fun clearOpenedBook() {
        _uiState.update { it.copy(openedBook = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

}

data class HomeUiState(
    val isLoading: Boolean = false,
    val openedBook: EpubBook? = null,
    val error: String? = null,
    val recentBooks: List<RecentBook> = emptyList(),
    val directoryFiles: List<File> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet()
)
