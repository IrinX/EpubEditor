package com.example.epubeditor.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.epubeditor.BuildConfig
import com.example.epubeditor.R
import com.example.epubeditor.data.repository.AppLanguage
import com.example.epubeditor.data.repository.DarkMode
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDarkDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsSectionTitle(stringResource(R.string.settings_appearance))

        SettingsListItem(
            icon = Icons.Default.DarkMode,
            title = stringResource(R.string.settings_dark_mode),
            summary = darkModeLabel(prefs.darkMode),
            onClick = { showDarkDialog = true }
        )

        SettingsListItem(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_dynamic_color),
            summary = stringResource(R.string.settings_dynamic_color_summary),
            trailing = {
                Switch(
                    checked = prefs.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            },
            onClick = { viewModel.setDynamicColor(!prefs.dynamicColor) }
        )

        SettingsSectionTitle(stringResource(R.string.settings_general))

        SettingsListItem(
            icon = Icons.Default.Language,
            title = stringResource(R.string.settings_language),
            summary = languageLabel(prefs.language),
            onClick = { showLanguageDialog = true }
        )

        SettingsSectionTitle(stringResource(R.string.settings_about))

        SettingsListItem(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_about_title),
            summary = stringResource(R.string.settings_version_summary, BuildConfig.VERSION_NAME),
            onClick = { showAboutDialog = true }
        )

        SettingsListItem(
            icon = Icons.Default.Brush,
            title = stringResource(R.string.settings_licenses),
            summary = stringResource(R.string.settings_licenses_summary),
            onClick = { showLicenseDialog = true }
        )
    }

    if (showDarkDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_dark_mode),
            options = DarkMode.entries.map { darkModeLabel(it) },
            selectedIndex = DarkMode.entries.indexOf(prefs.darkMode),
            onSelect = { index ->
                viewModel.setDarkMode(DarkMode.entries[index])
                showDarkDialog = false
            },
            onDismiss = { showDarkDialog = false }
        )
    }

    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries.map { languageLabel(it) },
            selectedIndex = AppLanguage.entries.indexOf(prefs.language),
            onSelect = { index ->
                viewModel.setLanguage(AppLanguage.entries[index])
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.settings_about_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_repo_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/IrinX/EpubEditor"))
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LicenseItem(
                        name = "Android Jetpack / Compose",
                        license = "Apache-2.0",
                        url = "https://developer.android.com/jetpack"
                    )
                    LicenseItem(
                        name = "Material Design 3",
                        license = "Apache-2.0",
                        url = "https://m3.material.io/"
                    )
                    LicenseItem(
                        name = "Dagger Hilt",
                        license = "Apache-2.0",
                        url = "https://dagger.dev/hilt/"
                    )
                    LicenseItem(
                        name = "Jsoup",
                        license = "MIT",
                        url = "https://jsoup.org/"
                    )
                    LicenseItem(
                        name = "Apache Commons Compress",
                        license = "Apache-2.0",
                        url = "https://commons.apache.org/proper/commons-compress/"
                    )
                    LicenseItem(
                        name = "Coil",
                        license = "Apache-2.0",
                        url = "https://coil-kt.github.io/coil/"
                    )
                    LicenseItem(
                        name = "Kotlinx Serialization",
                        license = "Apache-2.0",
                        url = "https://github.com/Kotlin/kotlinx.serialization"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsListItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LicenseItem(name: String, license: String, url: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.settings_open_link))
        }
    }
}

@Composable
private fun darkModeLabel(mode: DarkMode): String {
    return stringResource(
        when (mode) {
            DarkMode.LIGHT -> R.string.settings_dark_mode_light
            DarkMode.DARK -> R.string.settings_dark_mode_dark
            DarkMode.SYSTEM -> R.string.settings_dark_mode_system
        }
    )
}

@Composable
private fun languageLabel(language: AppLanguage): String {
    return stringResource(
        when (language) {
            AppLanguage.SYSTEM -> R.string.settings_language_system
            AppLanguage.ENGLISH -> R.string.settings_language_english
            AppLanguage.CHINESE -> R.string.settings_language_chinese
        }
    )
}

