package com.aybrkaknc.easyandroid.ui

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.BuildConfig
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.RootState
import com.aybrkaknc.easyandroid.ThemeMode
import com.aybrkaknc.easyandroid.core.DEFAULT_ROOT
import com.aybrkaknc.easyandroid.core.treePath
import com.aybrkaknc.easyandroid.job.UiText

private val Blue = Color(0xFF2E9CF5)
private val Teal = Color(0xFF2EC4B6)
private val Green = Color(0xFF3DDC84)
val Amber = Color(0xFFF5A623)

@StringRes
private fun ThemeMode.title(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.BLACK -> R.string.theme_black
}

/** Settings in groups, like the app they follow: each row opens a page, a dialog or another app. */
@Composable
fun SettingsScreen(model: MainViewModel, onPage: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val settings by Env.prefs.state.collectAsStateWithLifecycle()
    val root by model.root.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = treePath(uri.authority, DocumentsContract.getTreeDocumentId(uri))
            if (path == null) {
                model.error.value = UiText(R.string.folder_unsupported)
            } else {
                Env.prefs.setBackupRoot(path)
                model.refreshBackups()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        SettingsHeader(stringResource(R.string.settings_group_appearance))
        CardGroup(2) { index, shape ->
            if (index == 0) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    SettingCard(
                        stringResource(R.string.settings_theme), shape, summary = stringResource(settings.theme.title()),
                        icon = painterResource(R.drawable.ic_moon), tint = Blue, onClick = { menu = true },
                    ) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    DropdownMenu(menu, { menu = false }, offset = DpOffset(16.dp, 0.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.title())) },
                                onClick = {
                                    Env.prefs.setTheme(mode)
                                    menu = false
                                },
                                trailingIcon = { if (mode == settings.theme) Icon(Icons.Default.Check, null) },
                            )
                        }
                    }
                }
            } else {
                SettingCard(
                    stringResource(R.string.language), shape, summary = languageName(appLanguage(context)),
                    icon = painterResource(R.drawable.ic_language), tint = Teal, onClick = { onPage(SettingsPage.LANGUAGE) },
                )
            }
        }

        SettingsHeader(stringResource(R.string.settings_group_storage))
        CardGroup(2) { index, shape ->
            if (index == 0) {
                SettingCard(
                    stringResource(R.string.backup_folder), shape, summary = settings.backupRoot,
                    icon = painterResource(R.drawable.ic_storage), tint = Green, onClick = { pick.launch(null) },
                ) {
                    if (settings.backupRoot != DEFAULT_ROOT) {
                        TextButton({
                            Env.prefs.setBackupRoot(null)
                            model.refreshBackups()
                        }) { Text(stringResource(R.string.reset)) }
                    }
                }
            } else {
                SettingCard(
                    stringResource(R.string.storage_space), shape, summary = stringResource(R.string.storage_space_text),
                    icon = painterResource(R.drawable.ic_donut), tint = Green, onClick = { onPage(SettingsPage.STORAGE) },
                )
            }
        }

        SettingsHeader(stringResource(R.string.settings_group_notifications))
        CardGroup(1) { _, shape ->
            SettingCard(
                stringResource(R.string.notif_app), shape, summary = stringResource(R.string.notif_app_text),
                icon = rememberVectorPainter(Icons.Default.Notifications), tint = Amber, onClick = { notificationSettings(context) },
            )
        }

        SettingsHeader(stringResource(R.string.settings_group_advanced))
        CardGroup(2) { index, shape ->
            if (index == 0) {
                SettingCard(
                    stringResource(R.string.advanced_reports), shape, summary = stringResource(R.string.advanced_reports_text),
                    icon = painterResource(R.drawable.ic_bug), tint = Category.CALLS, onClick = { onPage(SettingsPage.REPORTS) },
                )
            } else {
                SettingCard(
                    stringResource(R.string.advanced_restart), shape, summary = stringResource(R.string.advanced_restart_text),
                    icon = painterResource(R.drawable.ic_restart), tint = Blue, onClick = { restartApp(context) },
                )
            }
        }

        SettingsHeader(stringResource(R.string.settings_group_help))
        CardGroup(5) { index, shape ->
            when (index) {
                0 -> SettingCard(
                    stringResource(R.string.help_center), shape, summary = stringResource(R.string.help_center_text),
                    icon = painterResource(R.drawable.ic_help), tint = Amber, onClick = { openUrl(context, helpUrl(context)) },
                )
                1 -> SettingCard(
                    stringResource(R.string.help_contact), shape, summary = stringResource(R.string.help_contact_text),
                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Send), tint = Amber, onClick = { openUrl(context, "$REPO/issues") },
                )
                2 -> SettingCard(
                    stringResource(R.string.help_rate), shape, summary = stringResource(R.string.help_rate_text),
                    icon = rememberVectorPainter(Icons.Default.Star), tint = Amber, onClick = { openUrl(context, REPO) },
                )
                3 -> SettingCard(
                    stringResource(R.string.help_share), shape, summary = stringResource(R.string.help_share_text),
                    icon = rememberVectorPainter(Icons.Default.Share), tint = Green, onClick = { shareApp(context) },
                )
                else -> SettingCard(
                    stringResource(R.string.about), shape, summary = stringResource(R.string.about_text_short),
                    icon = rememberVectorPainter(Icons.Default.Info), tint = Amber, onClick = { about = true },
                )
            }
        }
    }

    if (about) {
        ConfirmDialog(
            title = stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
            confirm = stringResource(R.string.close),
            onConfirm = { about = false },
            onDismiss = { about = false },
            dismiss = "",
        ) { Text(stringResource(R.string.about_text)) }
    }
}
