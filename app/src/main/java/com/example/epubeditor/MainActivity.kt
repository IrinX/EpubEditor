package com.example.epubeditor

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.epubeditor.R
import com.example.epubeditor.data.repository.AppLanguage
import com.example.epubeditor.data.repository.DarkMode
import com.example.epubeditor.ui.navigation.EpubNavHost
import com.example.epubeditor.ui.screens.settings.SettingsViewModel
import com.example.epubeditor.ui.theme.EpubEditorTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val prefs by settingsViewModel.preferences.collectAsState()
            val localizedContext = remember(prefs.language) {
                when (prefs.language) {
                    AppLanguage.ENGLISH -> this@MainActivity.withLocale(Locale.ENGLISH)
                    AppLanguage.CHINESE -> this@MainActivity.withLocale(Locale.SIMPLIFIED_CHINESE)
                    AppLanguage.SYSTEM -> this@MainActivity
                }
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                val darkTheme = when (prefs.darkMode) {
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                    DarkMode.SYSTEM -> isSystemInDarkTheme()
                }

                EpubEditorTheme(
                    darkTheme = darkTheme,
                    dynamicColor = prefs.dynamicColor
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        EpubNavHost()
                    }
                }
            }
        }
    }
}

private fun Context.withLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return ContextThemeWrapper(this, R.style.Theme_EpubEditor).apply {
        applyOverrideConfiguration(config)
    }
}
