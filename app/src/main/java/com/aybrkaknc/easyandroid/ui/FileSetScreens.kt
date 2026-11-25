package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.FileSetKind
import com.aybrkaknc.easyandroid.core.Folder
import com.aybrkaknc.easyandroid.core.StoredFileSet
import com.aybrkaknc.easyandroid.job.Jobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FileSetKind.title(): String = stringResource(titleOf(this))

/** Folders of shared storage to pick from. Listing them takes a moment, so a spinner shows first. */
@Composable
fun FolderPickerDialog(initial: Set<String>, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val folders by produceState<List<Folder>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { Env.files().folders() }.getOrDefault(emptyList()) }
    }
    var selected by remember { mutableStateOf(initial) }
    ConfirmDialog(
        title = stringResource(R.string.folder_picker_title),
        confirm = stringResource(R.string.folder_picker_confirm),
        onConfirm = { onConfirm(selected.toList()) },
        onDismiss = onDismiss,
        enabled = selected.isNotEmpty(),
    ) {
        Hint(stringResource(R.string.folder_picker_hint))
        val list = folders
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(stringResource(R.string.folder_picker_empty))
            else -> list.forEach { folder ->
                CheckRow(folder.name, folder.name in selected, hint = formatSize(folder.bytes)) { selected = selected.toggle(folder.name) }
            }
        }
    }
}

@Composable
private fun StoredFileSet.summary(): String {
    val parts = ArrayList<String>()
    parts += formatDate(set.createdAt)
    if (set.kind == FileSetKind.MEDIA || set.kind == FileSetKind.FOLDERS) {
        parts += stringResource(R.string.fileset_folders_count, set.items.sumOf { it.names.size })
    }
    parts += formatSize(set.totalBytes)
    return parts.joinToString("  ")
}

/** The saved backups of [kinds] and the button that makes a new one. The first kind is the one the button backs up. */
@Composable
fun FileSetBackups(model: MainViewModel, kinds: List<FileSetKind>) {
    val stored by model.backups.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<StoredFileSet?>(null) }
    var deleting by remember { mutableStateOf<StoredFileSet?>(null) }
    val list = stored ?: return Loading()
    val sets = list.fileSets.filter { it.set.kind in kinds }
    val kind = kinds.first()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (sets.isEmpty()) {
                Empty(stringResource(R.string.fileset_empty))
            } else {
                LazyColumn {
                    items(sets, key = { it.id }) { item ->
                        SettingRow(item.set.kind.title(), item.summary()) {
                            Row {
                                TextButton({ restoring = item }) { Text(stringResource(R.string.restore)) }
                                TextButton({ deleting = item }) { Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
        Button(
            { if (kind.fixed) startFileBackup(model, kind, emptyList()) else picking = true },
            Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(stringResource(if (kind.fixed) R.string.fileset_backup else R.string.fileset_pick)) }
    }

    if (picking) {
        FolderPickerDialog(emptySet(), onDismiss = { picking = false }) { names ->
            picking = false
            startFileBackup(model, kind, names)
        }
    }

    restoring?.let { item ->
        val title = item.set.kind.title()
        ConfirmDialog(
            title = stringResource(R.string.fileset_restore_title, title),
            confirm = stringResource(R.string.restore),
            onConfirm = {
                restoring = null
                startFileRestore(model, item)
            },
            onDismiss = { restoring = null },
        ) {
            Text(
                stringResource(if (item.set.kind.fixed) R.string.fileset_restore_replace else R.string.fileset_restore_files, title),
                color = MaterialTheme.colorScheme.error,
            )
            when (item.set.kind) {
                FileSetKind.WALLPAPER -> Hint(stringResource(R.string.fileset_hint_wallpaper))
                FileSetKind.CALLS, FileSetKind.MESSAGES -> Hint(stringResource(R.string.fileset_hint_database))
                else -> {}
            }
            Hint(item.id)
        }
    }

    deleting?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, 1),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                deleting = null
                model.run {
                    Env.store().delete(item.id)
                    model.refreshBackups()
                }
            },
            onDismiss = { deleting = null },
        ) {
            Text(stringResource(R.string.delete_warning), color = MaterialTheme.colorScheme.error)
            Hint(item.id)
        }
    }
}

/** [folders] only matter for media and folder backups. */
fun startFileBackup(model: MainViewModel, kind: FileSetKind, folders: List<String>) {
    val engine = Env.files()
    Jobs.start(Env.app, R.string.job_backup, listOf(kind), { Env.app.getString(titleOf(it)) }, after = { model.refreshBackups() }) { item, task ->
        engine.backup(item, folders, Env.backups().snapshot(), task)
        emptyList()
    }
}

private fun startFileRestore(model: MainViewModel, item: StoredFileSet) {
    val engine = Env.files()
    Jobs.start(Env.app, R.string.job_restore, listOf(item), { Env.app.getString(titleOf(it.set.kind)) }, after = { model.refreshBackups() }) { stored, task ->
        engine.restore(stored.id, task)
        emptyList()
    }
}

private fun titleOf(kind: FileSetKind): Int = when (kind) {
    FileSetKind.MEDIA -> R.string.kind_media
    FileSetKind.FOLDERS -> R.string.chip_folders
    FileSetKind.WALLPAPER -> R.string.chip_wallpapers
    FileSetKind.CALLS -> R.string.chip_calls
    FileSetKind.MESSAGES -> R.string.chip_messages
}
