package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.Stage
import com.aybrkaknc.easyandroid.core.SystemBackup
import com.aybrkaknc.easyandroid.core.SystemItem
import com.aybrkaknc.easyandroid.core.SystemSnapshot
import com.aybrkaknc.easyandroid.job.Jobs

@Composable
fun SystemScreen(model: MainViewModel) {
    val stored by model.backups.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(true) }
    var roles by remember { mutableStateOf(true) }
    var wifi by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var diff by remember { mutableStateOf<List<SystemItem>?>(null) }

    diff?.let { items ->
        return DiffScreen(items, onBack = { diff = null }) { chosen ->
            diff = null
            startApply(chosen)
        }
    }
    val list = stored ?: return Loading()
    if (loading) return Loading()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            WirelessSection(model)
            Heading(stringResource(R.string.system_create))
            Hint(stringResource(R.string.system_hint))
            CheckRow(stringResource(R.string.system_settings), settings) { settings = it }
            CheckRow(stringResource(R.string.system_roles), roles) { roles = it }
            CheckRow(stringResource(R.string.system_wifi), wifi, hint = stringResource(R.string.system_wifi_hint)) { wifi = it }
            Button(
                {
                    startCreate(model, settings, roles, wifi)
                },
                Modifier.fillMaxWidth(),
                enabled = settings || roles || wifi,
            ) { Text(stringResource(R.string.back_up)) }
            Heading(stringResource(R.string.system_saved))
            if (list.systemSnapshots.isEmpty()) Hint(stringResource(R.string.no_backups))
        }
        items(list.systemSnapshots, key = { it }) { name ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton({
                        loading = true
                        model.run {
                            try {
                                diff = Env.system().diff(SystemSnapshot.parse(Env.store().readText(name)))
                            } finally {
                                loading = false
                            }
                        }
                    }) { Text(stringResource(R.string.restore)) }
                    TextButton({ deleting = name }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }

    deleting?.let { name ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, 1),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                deleting = null
                model.run {
                    Env.store().delete(name)
                    model.refreshBackups()
                }
            },
            onDismiss = { deleting = null },
        ) {
            Text(stringResource(R.string.delete_warning), color = MaterialTheme.colorScheme.error)
            Hint(name)
        }
    }
}

@Composable
private fun DiffScreen(items: List<SystemItem>, onBack: () -> Unit, onApply: (List<SystemItem>) -> Unit) {
    var selected by remember { mutableStateOf(items.filter(SystemBackup::isCommon).toSet()) }
    var confirming by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.system_diff), style = MaterialTheme.typography.titleLarge)
        Hint(stringResource(R.string.system_diff_hint))
        Row {
            TextButton({ selected = items.toSet() }) { Text(stringResource(R.string.select_all)) }
            TextButton({ selected = emptySet() }) { Text(stringResource(R.string.select_none)) }
        }
        if (items.isEmpty()) Hint(stringResource(R.string.system_no_diff))
        LazyColumn(Modifier.weight(1f)) {
            items(items.sortedByDescending(SystemBackup::isCommon)) { item ->
                CheckRow(itemTitle(item), item in selected, hint = itemHint(item)) { selected = selected.toggle(item) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ confirming = true }, Modifier.weight(1f), enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.apply_count, selected.size))
            }
            OutlinedButton(onBack) { Text(stringResource(R.string.cancel)) }
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.apply_count, selected.size),
            confirm = stringResource(R.string.apply),
            onConfirm = { onApply(items.filter { it in selected }) },
            onDismiss = { confirming = false },
        ) { Text(stringResource(R.string.system_apply_warning)) }
    }
}

private fun plainTitle(item: SystemItem): String = when (item) {
    is SystemItem.Setting -> item.namespace + "/" + item.key
    is SystemItem.Role -> item.role.substringAfterLast('.')
    is SystemItem.Wifi -> item.network.ssid
}

@Composable
private fun itemTitle(item: SystemItem): String =
    if (item is SystemItem.Wifi) stringResource(R.string.wifi_item, item.network.ssid) else plainTitle(item)

@Composable
private fun itemHint(item: SystemItem): String = when (item) {
    is SystemItem.Setting -> stringResource(R.string.change, (item.current ?: "-").take(80), item.value.take(80))
    is SystemItem.Role -> stringResource(R.string.change, item.current ?: "-", item.packageName)
    is SystemItem.Wifi -> item.network.security
}

private fun startCreate(model: MainViewModel, settings: Boolean, roles: Boolean, wifi: Boolean) {
    Jobs.start(
        Env.app, R.string.job_system_backup, listOf(Unit), { Env.app.getString(R.string.tab_system) },
        after = { model.refreshBackups() },
    ) { _, task ->
        task.progress(Stage.SETTINGS)
        Env.system().create(settings, roles, wifi, Env.backups().snapshot())
        emptyList()
    }
}

private fun startApply(items: List<SystemItem>) {
    val system = Env.system()
    Jobs.start(Env.app, R.string.job_system_restore, items, ::plainTitle) { item, task ->
        task.progress(Stage.SETTINGS)
        system.apply(item)
        emptyList()
    }
}
