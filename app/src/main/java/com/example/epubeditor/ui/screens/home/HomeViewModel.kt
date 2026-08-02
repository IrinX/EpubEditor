package com.example.epubeditor.ui.screens.home

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
            }
        }
    }

    fun openFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                takePersistableUriPermission(uri)
                val book = repository.openFromUri(uri)
                settingsRepository.addRecentBook(book, sourceUri = uri, sourceFile = book.sourceFile)
                _uiState.update { it.copy(isLoading = false, openedBook = book) }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.home_recent_permission_lost)) }
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

    fun clearOpenedBook() {
        _uiState.update { it.copy(openedBook = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun takePersistableUriPermission(uri: Uri) {
        try {
            val mode = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, mode)
        } catch (_: SecurityException) {
            // Provider may not support persistable permission; ignore and let openFromUri fail naturally if needed.
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val openedBook: EpubBook? = null,
    val error: String? = null,
    val recentBooks: List<RecentBook> = emptyList()
)
