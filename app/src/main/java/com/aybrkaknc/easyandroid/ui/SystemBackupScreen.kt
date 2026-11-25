package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.Stage
import com.aybrkaknc.easyandroid.core.SystemItem
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Part(@param:StringRes val title: Int) {
    SETTINGS(R.string.system_settings), ROLES(R.string.system_roles), WIFI(R.string.system_wifi),
}

private fun SystemItem.part() = when (this) {
    is SystemItem.Setting -> Part.SETTINGS
    is SystemItem.Role -> Part.ROLES
    is SystemItem.Wifi -> Part.WIFI
}

private fun SystemItem.id() = when (this) {
    is SystemItem.Setting -> "s:$namespace:$key"
    is SystemItem.Role -> "r:$role:$packageName"
    is SystemItem.Wifi -> "w:${network.ssid}:${network.security}"
}

private fun SystemItem.title() = when (this) {
    is SystemItem.Setting -> key
    is SystemItem.Role -> role.substringAfterLast('.')
    is SystemItem.Wifi -> network.ssid
}

private fun SystemItem.summary() = when (this) {
    is SystemItem.Setting -> "$namespace: ${value.take(80)}"
    is SystemItem.Role -> packageName
    is SystemItem.Wifi -> network.security
}

/** Chooses which settings, default apps and Wi-Fi networks go into a new system backup. */
@Composable
fun SystemBackupScreen(model: MainViewModel, onDone: () -> Unit) {
    val items by produceState<List<SystemItem>?>(null) {
        value = withContext(Dispatchers.IO) {
            try {
                val system = Env.system()
                // Phones without the Wi-Fi store still back up everything else.
                system.available(wifi = false) + runCatching { system.available(settings = false, roles = false) }.getOrDefault(emptyList())
            } catch (e: Exception) {
                model.error.value = errorText(e)
                emptyList()
            }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf(emptySet<Part>()) }

    val all = items ?: return Loading()
    // Passwords stay out of a backup unless the user asks for them.
    var selected by remember(all) { mutableStateOf(all.filter { it.part() != Part.WIFI }.toSet()) }
    val visible = all.filter {
        query.isBlank() || it.title().contains(query, true) || it.summary().contains(query, true)
    }
    // Wi-Fi networks are never part of select all, for the same reason they are not selected by default.
    val selectable = all.filter { it.part() != Part.WIFI }
    val allSelected = selectable.isNotEmpty() && selected.containsAll(selectable)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.apps_selected_count, selectable.count { it in selected }, selectable.size),
                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            )
            IconButton({
                searching = !searching
                if (!searching) query = ""
            }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
            SelectAllButton(
                allSelected, selectable.any { it in selected },
                stringResource(if (allSelected) R.string.select_none else R.string.select_all),
            ) { selected = if (allSelected) selected - selectable.toSet() else selected + selectable }
        }
        if (searching) SearchField(query) { query = it }
        LazyColumn(Modifier.weight(1f)) {
            Part.entries.forEach { part ->
                val total = all.filter { it.part() == part }
                if (total.isEmpty()) return@forEach
                val shown = visible.filter { it.part() == part }
                val count = total.count { it in selected }
                item(key = "header:$part") {
                    SettingRow(
                        stringResource(part.title),
                        summary = "$count / ${total.size}",
                        onClick = { open = if (part in open) open - part else open + part },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(
                                when (count) {
                                    0 -> ToggleableState.Off
                                    total.size -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                },
                                onClick = { selected = if (count == total.size) selected - total.toSet() else selected + total },
                            )
                            Icon(
                                if (part in open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (part in open) {
                    items(shown, key = { it.id() }) { item ->
                        SettingRow(
                            item.title(),
                            summary = item.summary(),
                            onClick = { selected = selected.toggle(item) },
                        ) { Checkbox(item in selected, null) }
                    }
                    if (part == Part.WIFI) item(key = "hint:wifi") {
                        Text(
                            stringResource(R.string.system_wifi_hint),
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            {
                startBackup(model, all.filter { it in selected })
                onDone()
            },
            Modifier.fillMaxWidth().padding(16.dp),
            enabled = selected.isNotEmpty(),
        ) { Text(stringResource(R.string.back_up_count, selected.size)) }
    }
}

private fun startBackup(model: MainViewModel, items: List<SystemItem>) {
    Jobs.start(
        Env.app, R.string.job_system_backup, listOf(Unit), { Env.app.getString(R.string.tab_system) },
        after = { model.refreshBackups() },
    ) { _, task ->
        task.progress(Stage.SETTINGS)
        Env.system().save(items, Env.backups().snapshot())
        emptyList()
    }
}
