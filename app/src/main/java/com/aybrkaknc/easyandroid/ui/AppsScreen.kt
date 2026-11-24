package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.AppInfo
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.BackupOptions
import com.aybrkaknc.easyandroid.core.Sh
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.noteText

@Composable
fun AppsScreen(model: MainViewModel) {
    val context = LocalContext.current
    val apps by model.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var asking by remember { mutableStateOf(false) }

    val all = apps ?: return Loading()
    val visible = remember(all, query, system) {
        all.filter { it.installed && it.system == system && it.packageName != context.packageName && Sh.isPackage(it.packageName) }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query) { query = it }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(!system, { system = false }, { Text(stringResource(R.string.user_apps)) })
            FilterChip(system, { system = true }, { Text(stringResource(R.string.system_apps)) })
            val names = visible.map { it.packageName }.toSet()
            val allSelected = names.isNotEmpty() && selected.containsAll(names)
            TextButton({ selected = if (allSelected) selected - names else selected + names }) {
                Text(stringResource(if (allSelected) R.string.select_none else R.string.select_all))
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.packageName }) { app ->
                SelectRow(
                    packageName = app.packageName,
                    title = app.label,
                    subtitle = app.packageName,
                    detail = listOf(app.versionName, formatSize(app.apkBytes)).filter { it.isNotEmpty() }.joinToString("  "),
                    checked = app.packageName in selected,
                ) { selected = selected.toggle(app.packageName) }
            }
        }
        if (selected.isNotEmpty()) {
            Button({ asking = true }, Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.back_up_count, selected.size))
            }
        }
    }

    if (asking) {
        BackupDialog(selected.size, onDismiss = { asking = false }) { options ->
            asking = false
            val chosen = all.filter { it.packageName in selected }
            selected = emptySet()
            startBackup(model, chosen, options)
        }
    }
}

fun startBackup(model: MainViewModel, apps: List<AppInfo>, options: BackupOptions) {
    val engine = Env.backups()
    val device by lazy { engine.snapshot() }
    Jobs.start(Env.app, R.string.job_backup, apps, { it.label }, after = { model.refreshBackups() }) { app, task ->
        engine.backup(app.packageName, app.label, options, device, task).notes.map(::noteText)
    }
}

@Composable
fun BackupDialog(count: Int, onDismiss: () -> Unit, note: String? = null, onConfirm: (BackupOptions) -> Unit) {
    var options by remember { mutableStateOf(Env.prefs.state.value.backup) }
    ConfirmDialog(
        title = stringResource(R.string.back_up_count, count),
        confirm = stringResource(R.string.back_up),
        onConfirm = { onConfirm(options) },
        onDismiss = onDismiss,
    ) {
        CheckRow(stringResource(R.string.part_app), true, enabled = false) {}
        CheckRow(stringResource(R.string.part_data), options.data, hint = stringResource(R.string.part_data_hint)) {
            options = options.copy(data = it)
        }
        CheckRow(stringResource(R.string.part_obb), options.obb) { options = options.copy(obb = it) }
        CheckRow(stringResource(R.string.part_external), options.externalData, hint = stringResource(R.string.part_external_hint)) {
            options = options.copy(externalData = it)
        }
        Heading(stringResource(R.string.compression))
        RadioRow(stringResource(R.string.compression_none), !options.compress, stringResource(R.string.compression_none_hint)) {
            options = options.copy(compress = false)
        }
        RadioRow(stringResource(R.string.compression_zstd), options.compress, stringResource(R.string.compression_zstd_hint)) {
            options = options.copy(compress = true)
        }
        if (options.data || options.externalData) {
            Text(stringResource(R.string.force_stop_warning), color = MaterialTheme.colorScheme.error)
            if (note != null) Text(note, color = MaterialTheme.colorScheme.error)
        }
    }
}
