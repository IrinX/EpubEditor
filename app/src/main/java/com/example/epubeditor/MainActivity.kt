package com.example.epubeditor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.epubeditor.data.repository.DarkMode
import com.example.epubeditor.ui.navigation.EpubNavHost
import com.example.epubeditor.ui.screens.settings.SettingsViewModel
import com.example.epubeditor.ui.theme.EpubEditorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        settingsViewModel.applyInitialLanguage()

        setContent {
            val prefs by settingsViewModel.preferences.collectAsState()
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
