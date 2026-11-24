package com.aybrkaknc.easyandroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.Archive
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.FileKind
import com.aybrkaknc.easyandroid.core.RestoreOptions
import com.aybrkaknc.easyandroid.core.StoredBackup
import com.aybrkaknc.easyandroid.core.SuRoot
import com.aybrkaknc.easyandroid.core.SystemItem
import com.aybrkaknc.easyandroid.core.SystemSnapshot
import com.aybrkaknc.easyandroid.core.parseTimestamp
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.UiText
import com.aybrkaknc.easyandroid.job.noteText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val StoredBackup.title get() = manifest.label ?: manifest.packageName

fun formatDate(text: String): String = parseTimestamp(text)
    ?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)) ?: text

@Composable
private fun StoredBackup.contents(): String {
    val parts = ArrayList<String>()
    if (manifest.has(FileKind.DATA)) parts += stringResource(R.string.part_data)
    if (manifest.has(FileKind.OBB)) parts += stringResource(R.string.part_obb)
    if (manifest.compressed) parts += "zstd"
    parts += formatSize(manifest.totalBytes)
    return parts.joinToString("  ")
}

/** What another screen wants to see in the Backups tab. */
sealed interface BackupsRequest {
    data class Select(val ids: Set<String>) : BackupsRequest
    data object System : BackupsRequest
}

@Composable
private fun BackupsSegment.title(): String = stringResource(
    when (this) {
        BackupsSegment.APPS -> R.string.chip_apps
        BackupsSegment.MESSAGES -> R.string.chip_messages
        BackupsSegment.CALLS -> R.string.chip_calls
        BackupsSegment.FOLDERS -> R.string.chip_folders
    },
)

/** The saved backups, by kind. Only apps and system settings have any yet. */
@Composable
fun BackupsScreen(model: MainViewModel, request: BackupsRequest?, onRequestHandled: () -> Unit) {
    var segment by rememberSaveable { mutableStateOf(BackupsSegment.APPS) }
    // A request from Home is about app or system backups, which are in the first segment.
    LaunchedEffect(request) { if (request != null) segment = BackupsSegment.APPS }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackupsSegment.entries.forEach { FilterChip(segment == it, { segment = it }, { Text(it.title()) }) }
        }
        Box(Modifier.weight(1f)) {
            when (segment) {
                BackupsSegment.APPS -> AppBackups(model, request, onRequestHandled)
                BackupsSegment.MESSAGES -> ListPlaceholderScreen(calls = false)
                BackupsSegment.CALLS -> ListPlaceholderScreen(calls = true)
                BackupsSegment.FOLDERS -> FolderBackupsScreen()
            }
        }
    }
}

@Composable
private fun AppBackups(model: MainViewModel, request: BackupsRequest?, onRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val stored by model.backups.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var choosing by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf<Conflicts?>(null) }
    var deleting by remember { mutableStateOf<List<String>?>(null) }
    var systemDiff by remember { mutableStateOf<List<SystemItem>?>(null) }
    var systemLoading by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = selected.singleOrNull()
        if (uri != null && id != null) startExport(id, uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startImport(model, uri)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(request, stored) {
        val ready = stored ?: return@LaunchedEffect
        when (request) {
            null -> return@LaunchedEffect
            is BackupsRequest.Select -> {
                query = ""
                selected = request.ids
            }
            BackupsRequest.System -> {
                query = ""
                if (ready.systemSnapshots.isNotEmpty()) listState.scrollToItem(ready.backups.size + ready.broken.size)
            }
        }
        onRequestHandled()
    }

    systemDiff?.let { items ->
        return DiffScreen(items, onBack = { systemDiff = null }) { chosen ->
            systemDiff = null
            startApply(chosen)
        }
    }
    val list = stored ?: return Loading()
    if (systemLoading) return Loading()
    val visible = list.backups.filter {
        query.isBlank() || it.title.contains(query, true) || it.manifest.packageName.contains(query, true)
    }
    val chosen = list.backups.filter { it.id in selected }

    fun restore(backups: List<StoredBackup>, options: RestoreOptions) {
        selected = emptySet()
        startRestore(model, backups, options)
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query) { query = it }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton({ import.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_backup)) }
            TextButton({ model.refreshBackups() }) { Text(stringResource(R.string.refresh)) }
            if (visible.isNotEmpty()) {
                val newest = visible.distinctBy { it.manifest.packageName }.map { it.id }.toSet()
                TextButton({ selected = if (selected == newest) emptySet() else newest }) {
                    Text(stringResource(if (selected == newest) R.string.select_none else R.string.select_newest))
                }
            }
        }
        if (list.backups.isEmpty() && list.broken.isEmpty() && list.systemSnapshots.isEmpty()) {
            Empty(stringResource(R.string.no_backups))
        } else {
            LazyColumn(Modifier.weight(1f), listState) {
                items(visible, key = { it.id }) { backup ->
                    SelectRow(
                        packageName = backup.manifest.packageName,
                        title = backup.title + "  " + backup.manifest.versionName.orEmpty(),
                        subtitle = formatDate(backup.manifest.createdAt) + "  " + backup.manifest.device.model,
                        detail = backup.contents(),
                        checked = backup.id in selected,
                    ) { selected = selected.toggle(backup.id) }
                }
                items(list.broken, key = { it }) { id ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(id, style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.incomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton({ deleting = listOf(id) }) { Text(stringResource(R.string.delete)) }
                    }
                }
                if (list.systemSnapshots.isNotEmpty()) {
                    item(key = "system-header") { SettingsHeader(stringResource(R.string.system_saved)) }
                    items(list.systemSnapshots, key = { it }) { name ->
                        SettingRow(name) {
                            Row {
                                TextButton({
                                    systemLoading = true
                                    model.run {
                                        try {
                                            systemDiff = Env.system().diff(SystemSnapshot.parse(Env.store().readText(name)))
                                        } finally {
                                            systemLoading = false
                                        }
                                    }
                                }) { Text(stringResource(R.string.restore)) }
                                TextButton({ deleting = listOf(name) }) { Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
        if (chosen.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ choosing = true }, Modifier.weight(1f)) { Text(stringResource(R.string.restore_count, chosen.size)) }
                if (chosen.size == 1) {
                    OutlinedButton({ export.launch(chosen[0].id + ".easybckp") }) { Text(stringResource(R.string.export)) }
                }
                OutlinedButton({ deleting = chosen.map { it.id } }) { Text(stringResource(R.string.delete)) }
            }
        }
    }

    if (choosing) {
        RestoreDialog(chosen, onDismiss = { choosing = false }) { options ->
            choosing = false
            val installed = apps.orEmpty().filter { it.installed }.associateBy { it.packageName }
            val (existing, missing) = chosen.partition { it.manifest.packageName in installed }
            if (existing.isEmpty()) restore(missing, options)
            else conflicts = Conflicts(existing, missing, options)
        }
    }

    conflicts?.let { state ->
        val backup = state.pending.first()
        var forAll by remember(backup.id) { mutableStateOf(false) }
        fun decide(overwrite: Boolean) {
            val decided = if (forAll) state.pending else listOf(backup)
            val next = state.copy(pending = state.pending - decided.toSet(), accepted = if (overwrite) state.accepted + decided else state.accepted)
            if (next.pending.isEmpty()) {
                conflicts = null
                restore(next.accepted, next.options)
            } else {
                conflicts = next
            }
        }
        val current = apps.orEmpty().firstOrNull { it.packageName == backup.manifest.packageName }
        ConfirmDialog(
            title = stringResource(R.string.already_installed, backup.title),
            confirm = stringResource(R.string.overwrite),
            onConfirm = { decide(true) },
            onDismiss = { conflicts = null },
            dismiss = stringResource(R.string.skip),
            onNegative = { decide(false) },
        ) {
            Text(stringResource(R.string.installed_version, current?.versionName.orEmpty()))
            Text(stringResource(R.string.backup_version, backup.manifest.versionName.orEmpty(), formatDate(backup.manifest.createdAt)))
            Text(stringResource(R.string.overwrite_warning), color = MaterialTheme.colorScheme.error)
            if (state.pending.size > 1) {
                CheckRow(stringResource(R.string.for_all, state.pending.size), forAll) { forAll = it }
            }
        }
    }

    deleting?.let { ids ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, ids.size),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                deleting = null
                selected = selected - ids.toSet()
                model.run {
                    val store = Env.store()
                    ids.forEach(store::delete)
                    model.refreshBackups()
                }
            },
            onDismiss = { deleting = null },
        ) {
            Text(stringResource(R.string.delete_warning), color = MaterialTheme.colorScheme.error)
            ids.forEach { Hint(it) }
        }
    }
}

private data class Conflicts(
    val pending: List<StoredBackup>,
    val accepted: List<StoredBackup>,
    val options: RestoreOptions,
)

@Composable
private fun RestoreDialog(backups: List<StoredBackup>, onDismiss: () -> Unit, onConfirm: (RestoreOptions) -> Unit) {
    var options by remember { mutableStateOf(RestoreOptions()) }
    ConfirmDialog(
        title = stringResource(R.string.restore_count, backups.size),
        confirm = stringResource(R.string.restore),
        onConfirm = { onConfirm(options) },
        onDismiss = onDismiss,
        enabled = options.app || options.data || options.obb || options.permissions,
    ) {
        CheckRow(stringResource(R.string.part_app), options.app) { options = options.copy(app = it) }
        CheckRow(stringResource(R.string.part_all_data), options.data) { options = options.copy(data = it) }
        CheckRow(stringResource(R.string.part_obb), options.obb) { options = options.copy(obb = it) }
        CheckRow(stringResource(R.string.part_permissions), options.permissions, hint = stringResource(R.string.part_permissions_hint)) {
            options = options.copy(permissions = it)
        }
        Hint(stringResource(R.string.keystore_hint))
    }
}

private fun startRestore(model: MainViewModel, backups: List<StoredBackup>, options: RestoreOptions) {
    val engine = Env.restores()
    Jobs.start(Env.app, R.string.job_restore, backups, { it.title }, after = { model.refresh() }) { backup, task ->
        val outcome = engine.restore(backup.id, options, android.os.Build.VERSION.SDK_INT, task)
        val lines = outcome.notes.map(::noteText).toMutableList()
        if (outcome.permissionsWanted > 0 && outcome.permissionsGranted == outcome.permissionsWanted) {
            lines += UiText(R.string.permissions_restored, listOf(outcome.permissionsGranted.toString()))
        }
        lines
    }
}

private fun startExport(id: String, uri: Uri) {
    Jobs.start(Env.app, R.string.job_export, listOf(id), { it }) { item, task ->
        val output = Env.app.contentResolver.openOutputStream(uri, "wt") ?: throw AppException(ErrorCode.NOT_FOUND, uri.toString())
        Archive(SuRoot, Env.store(), task).export(item, output)
        emptyList()
    }
}

private fun startImport(model: MainViewModel, uri: Uri) {
    Jobs.start(Env.app, R.string.job_import, listOf(uri), { it.lastPathSegment.orEmpty().substringAfterLast('/') }, after = { model.refreshBackups() }) { item, task ->
        val input = Env.app.contentResolver.openInputStream(item) ?: throw AppException(ErrorCode.NOT_FOUND, item.toString())
        input.use { Archive(SuRoot, Env.store(), task).import(it) }
        emptyList()
    }
}
