package com.example.epubeditor.data.repository

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class DarkMode {
    LIGHT, DARK, SYSTEM
}

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    CHINESE("zh-CN")
}

@Serializable
data class RecentBook(
    val id: String,
    val title: String,
    val uri: String,
    val internalPath: String = "",
    val originalUri: String = "",
    val openedAt: Long
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val RECENT_BOOKS = stringPreferencesKey("recent_books")
    }

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            darkMode = runCatching { DarkMode.valueOf(prefs[Keys.DARK_MODE] ?: "SYSTEM") }.getOrDefault(DarkMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            language = runCatching { AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM),
            recentBooks = parseRecentBooks(prefs[Keys.RECENT_BOOKS])
        )
    }

    suspend fun setDarkMode(mode: DarkMode) {
        dataStore.edit { it[Keys.DARK_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.name }
        applyLanguage(language)
    }

    suspend fun addRecentBook(book: com.example.epubeditor.data.epub.model.EpubBook, sourceUri: Uri? = null, sourceFile: File? = null) {
        val originalUriString = sourceUri?.toString() ?: book.originalUri?.toString() ?: ""
        val internalPathString = sourceFile?.absolutePath ?: ""
        val uriString = originalUriString.ifBlank { internalPathString }
        val newEntry = RecentBook(
            id = book.id,
            title = book.opf.metadata.title.ifBlank { book.id },
            uri = uriString,
            internalPath = internalPathString,
            originalUri = originalUriString,
            openedAt = System.currentTimeMillis()
        )
        dataStore.edit { prefs ->
            val current = parseRecentBooks(prefs[Keys.RECENT_BOOKS])
            val filtered = current.filter { it.id != newEntry.id }
            val updated = (listOf(newEntry) + filtered).take(MAX_RECENT)
            prefs[Keys.RECENT_BOOKS] = Json.encodeToString(updated)
        }
    }

    suspend fun clearRecentBooks() {
        dataStore.edit { it[Keys.RECENT_BOOKS] = "[]" }
    }

    fun applyLanguage(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun parseRecentBooks(json: String?): List<RecentBook> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { Json.decodeFromString<List<RecentBook>>(json) }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_RECENT = 20
    }
}

data class AppPreferences(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val recentBooks: List<RecentBook> = emptyList()
)
