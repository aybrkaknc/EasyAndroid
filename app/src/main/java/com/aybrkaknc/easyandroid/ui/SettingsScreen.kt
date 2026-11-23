package com.aybrkaknc.easyandroid.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.BuildConfig
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.DEFAULT_ROOT
import com.aybrkaknc.easyandroid.core.DebloatProfile
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.ProfileEntry
import com.aybrkaknc.easyandroid.core.treePath
import com.aybrkaknc.easyandroid.job.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(model: MainViewModel) {
    val context = LocalContext.current
    val settings by Env.prefs.state.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    var plan by remember { mutableStateOf<List<ProfileEntry>?>(null) }

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
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val entries = removedProfile(model.apps.value.orEmpty())
        if (uri != null) model.run { saveProfile(uri, entries) }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.run {
            val text = Env.app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw AppException(ErrorCode.NOT_FOUND, uri.toString())
            val known = model.apps.value.orEmpty().map { it.packageName }.toSet()
            // Packages the new ROM does not have are left out.
            val entries = DebloatProfile.parse(text).entries.filter { it.packageName in known }
            withContext(Dispatchers.Main) { plan = entries }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Heading(stringResource(R.string.backup_folder))
        Text(settings.backupRoot)
        Hint(stringResource(R.string.backup_folder_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ pick.launch(null) }) { Text(stringResource(R.string.choose_folder)) }
            if (settings.backupRoot != DEFAULT_ROOT) {
                TextButton({
                    Env.prefs.setBackupRoot(null)
                    model.refreshBackups()
                }) { Text(stringResource(R.string.reset)) }
            }
        }

        Heading(stringResource(R.string.debloat_profile))
        Hint(stringResource(R.string.debloat_profile_hint))
        OutlinedButton({ save.launch("easyandroid-debloat.json") }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_save_state))
        }
        OutlinedButton({ load.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_load)) }

        Heading(stringResource(R.string.language))
        if (Build.VERSION.SDK_INT >= 33) {
            OutlinedButton({
                context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text(stringResource(R.string.change_language)) }
        } else {
            Hint(stringResource(R.string.language_system))
        }

        Heading(stringResource(R.string.about))
        Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
        Hint(stringResource(R.string.about_text))
    }

    plan?.let { entries ->
        DebloatPlanDialog(entries, apps.orEmpty(), onDismiss = { plan = null }) {
            plan = null
            startDebloat(model, apps.orEmpty(), entries)
        }
    }
}
