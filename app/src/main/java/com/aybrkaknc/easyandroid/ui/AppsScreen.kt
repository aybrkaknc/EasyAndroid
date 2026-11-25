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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.AppInfo
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.BackupOptions
import com.aybrkaknc.easyandroid.core.Sh
import com.aybrkaknc.easyandroid.core.parseTimestamp
import com.aybrkaknc.easyandroid.core.PcLink
import com.aybrkaknc.easyandroid.core.PcRequest
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.noteText
import com.aybrkaknc.easyandroid.job.pcNoteText

enum class AppSort { NAME, INSTALL, UPDATE, BACKUP_DATE, BACKUP_SIZE }

enum class EnabledFilter { ALL, ENABLED, DISABLED }

/**
 * Choose the apps to back up, then open the options with the button at the bottom. The quick actions
 * open this screen with their apps already ticked in [preselected].
 */
@Composable
fun AppsScreen(model: MainViewModel, preselected: Set<String> = emptySet()) {
    val context = LocalContext.current
    val apps by model.apps.collectAsStateWithLifecycle()
    val stored by model.backups.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var system by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var enabledFilter by rememberSaveable { mutableStateOf(EnabledFilter.ALL) }
    var selected by rememberSaveable(preselected) { mutableStateOf(preselected) }
    var asking by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }

    val all = apps ?: return Loading()
    // The newest backup of every package, to show its state and to sort by it.
    val newest = remember(stored) { stored?.backups.orEmpty().distinctBy { it.manifest.packageName }.associateBy { it.manifest.packageName } }
    val visible = remember(all, query, system, sort, enabledFilter, newest) {
        val base = all.filter { it.installed && it.system == system && it.packageName != context.packageName && Sh.isPackage(it.packageName) }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .filter {
                when (enabledFilter) {
                    EnabledFilter.ALL -> true
                    EnabledFilter.ENABLED -> it.enabled
                    EnabledFilter.DISABLED -> !it.enabled
                }
            }
        when (sort) {
            AppSort.NAME -> base
            AppSort.INSTALL -> base.sortedByDescending { it.installTime }
            AppSort.UPDATE -> base.sortedByDescending { it.updateTime }
            AppSort.BACKUP_DATE -> base.sortedByDescending {
                newest[it.packageName]?.let { b -> parseTimestamp(b.manifest.createdAt)?.toInstant()?.toEpochMilli() } ?: 0L
            }
            AppSort.BACKUP_SIZE -> base.sortedByDescending { newest[it.packageName]?.manifest?.totalBytes ?: -1L }
        }
    }
    val names = visible.map { it.packageName }.toSet()
    val allSelected = names.isNotEmpty() && selected.containsAll(names)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.apps_selected_count, visible.count { it.packageName in selected }, visible.size),
                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            )
            IconButton({
                searching = !searching
                if (!searching) query = ""
            }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
            IconButton({ sorting = true }) { Icon(painterResource(R.drawable.ic_filter), stringResource(R.string.filter_title)) }
            SelectAllButton(
                allSelected, names.any { it in selected },
                stringResource(if (allSelected) R.string.select_none else R.string.select_all),
            ) { selected = if (allSelected) selected - names else selected + names }
        }
        if (searching) SearchField(query) { query = it }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(!system, { system = false }, { Text(stringResource(R.string.user_apps)) })
            FilterChip(system, { system = true }, { Text(stringResource(R.string.system_apps)) })
        }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.packageName }) { app ->
                val backup = newest[app.packageName]
                SelectRow(
                    packageName = app.packageName,
                    title = app.label,
                    subtitle = app.packageName,
                    detail = listOf(
                        app.versionName,
                        formatSize(app.apkBytes),
                        if (backup == null) stringResource(R.string.app_no_backup)
                        else stringResource(R.string.app_last_backup, formatDate(backup.manifest.createdAt)),
                    ).filter { it.isNotEmpty() }.joinToString("  "),
                    checked = app.packageName in selected,
                ) { selected = selected.toggle(app.packageName) }
            }
        }
        if (selected.isNotEmpty()) {
            ExtendedFloatingActionButton({ asking = true }, Modifier.align(Alignment.End).padding(16.dp)) {
                Icon(Icons.Default.Settings, null)
                Text(
                    stringResource(R.string.backup_options) + " (" + selected.size + ")",
                    Modifier.padding(start = 12.dp),
                )
            }
        }
    }

    if (asking) {
        val system = all.count { it.packageName in selected && it.system }
        BackupSheet(
            selected.size,
            onDismiss = { asking = false },
            note = if (system > 0) stringResource(R.string.quick_system_note, system) else null,
        ) { options, target ->
            asking = false
            val chosen = all.filter { it.packageName in selected }
            selected = emptySet()
            startBackup(model, chosen, options, target)
        }
    }
    if (sorting) {
        SortFilterSheet(sort, enabledFilter, onDismiss = { sorting = false }) { newSort, newFilter ->
            sorting = false
            sort = newSort
            enabledFilter = newFilter
        }
    }
}

fun startBackup(model: MainViewModel, apps: List<AppInfo>, options: BackupOptions, target: Target = Target.PHONE) {
    val engine = Env.backups()
    val device by lazy { engine.snapshot() }
    val batch = PcLink.newId()
    Jobs.start(Env.app, R.string.job_backup, apps, { it.label }, after = { model.refreshBackups() }) { app, task ->
        if (target == Target.PC) {
            val request = PcRequest(
                id = PcLink.newId(), created = 0, type = "app", packageName = app.packageName,
                data = options.data, obb = options.obb, externalData = options.externalData,
                batch = batch, index = apps.indexOf(app), size = apps.size,
            )
            Env.pc().send(request, task).notes.map(::pcNoteText)
        } else {
            engine.backup(app.packageName, app.label, options, device, task).notes.map(::noteText)
        }
    }
}
