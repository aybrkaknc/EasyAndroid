package com.aybrkaknc.easyandroid.ui

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R

const val REPO = "https://github.com/aybrkaknc/EasyAndroid"

enum class SettingsPage(@param:StringRes val title: Int) {
    LANGUAGE(R.string.language),
    STORAGE(R.string.storage_space),
    REPORTS(R.string.advanced_reports),
}

@Composable
fun SettingsPageContent(page: SettingsPage, model: MainViewModel) {
    when (page) {
        SettingsPage.LANGUAGE -> LanguagePage()
        SettingsPage.STORAGE -> StoragePage(model)
        SettingsPage.REPORTS -> ReportsScreen()
    }
}

/** Opens a link in whatever browser the phone has. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
    }
}

/** The how-to page in the language of the app. */
fun helpUrl(context: Context): String {
    val turkish = context.resources.configuration.locales[0].language == "tr"
    return "$REPO/blob/main/docs/" + if (turkish) "HOW_TO_USE.tr.md" else "HOW_TO_USE.md"
}

fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, REPO))
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun notificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Starts the app over again. The process is ended a moment later, so that the new start is not cut off. */
fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 300)
}

// ---- language

/** "en", "tr" or null for the system language. Older Android versions cannot choose. */
fun appLanguage(context: Context): String? {
    if (Build.VERSION.SDK_INT < 33) return null
    return readLanguage(context)
}

@RequiresApi(33)
private fun readLanguage(context: Context): String? {
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    return if (locales.isEmpty) null else locales[0].language
}

@RequiresApi(33)
private fun writeLanguage(context: Context, tag: String?) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
fun languageName(tag: String?): String = stringResource(
    when (tag) {
        "en" -> R.string.language_name_en
        "tr" -> R.string.language_name_tr
        else -> R.string.language_system
    },
)

@Composable
private fun LanguagePage() {
    val context = LocalContext.current
    val canChange = Build.VERSION.SDK_INT >= 33
    var current by remember { mutableStateOf(appLanguage(context)) }
    val options = listOf<String?>(null, "en", "tr")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        CardGroup(options.size) { index, shape ->
            val tag = options[index]
            SettingCard(
                languageName(tag), shape, enabled = canChange,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) writeLanguage(context, tag)
                    current = tag
                },
            ) { RadioButton(current == tag, null, enabled = canChange) }
        }
        if (!canChange) Hint(stringResource(R.string.language_old_android))
    }
}


// ---- storage

@Composable
private fun StoragePage(model: MainViewModel) {
    val context = LocalContext.current
    val settings by Env.prefs.state.collectAsStateWithLifecycle()
    val stored by model.backups.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    val list = stored
    val ids = list?.let { it.backups.map { b -> b.id } + it.broken + it.systemSnapshots }.orEmpty()
    val bytes = list?.backups?.sumOf { it.manifest.totalBytes } ?: 0L
    val free = remember { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        CardGroup(3) { index, shape ->
            when (index) {
                0 -> SettingCard(
                    stringResource(R.string.backup_folder), shape, summary = settings.backupRoot,
                    icon = painterResource(R.drawable.ic_storage), tint = androidx.compose.ui.graphics.Color(0xFF3DDC84),
                )
                1 -> SettingCard(
                    stringResource(R.string.storage_used, Formatter.formatFileSize(context, bytes)), shape,
                    summary = stringResource(R.string.storage_counts, list?.backups?.size ?: 0, list?.systemSnapshots?.size ?: 0),
                    icon = painterResource(R.drawable.ic_donut), tint = androidx.compose.ui.graphics.Color(0xFF3DDC84),
                )
                else -> SettingCard(
                    stringResource(R.string.storage_free, Formatter.formatFileSize(context, free)), shape,
                    icon = painterResource(R.drawable.ic_storage), tint = androidx.compose.ui.graphics.Color(0xFF3DDC84),
                )
            }
        }
        Column(Modifier.padding(top = 24.dp)) {
            CardGroup(1) { _, shape ->
                SettingCard(
                    stringResource(R.string.storage_delete_all), shape, danger = true, enabled = ids.isNotEmpty(),
                    onClick = { asking = true },
                )
            }
        }
    }

    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.storage_delete_all),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                asking = false
                model.run {
                    val store = Env.store()
                    ids.forEach(store::delete)
                    model.refreshBackups()
                }
            },
            onDismiss = { asking = false },
        ) {
            Text(stringResource(R.string.storage_delete_text, ids.size, Formatter.formatFileSize(context, bytes)))
        }
    }
}
