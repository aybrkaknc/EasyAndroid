package com.aybrkaknc.easyandroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.Stage
import com.aybrkaknc.easyandroid.core.SystemBackup
import com.aybrkaknc.easyandroid.core.SystemItem
import com.aybrkaknc.easyandroid.job.Jobs

/** The differences between a saved system backup and the phone, to choose from before restoring. */
@Composable
fun DiffScreen(items: List<SystemItem>, onBack: () -> Unit, onApply: (List<SystemItem>) -> Unit) {
    var selected by remember { mutableStateOf(items.filter(SystemBackup::isCommon).toSet()) }
    var confirming by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.system_diff), style = MaterialTheme.typography.titleLarge)
        Hint(stringResource(R.string.system_diff_hint))
        val all = items.toSet()
        val allSelected = all.isNotEmpty() && selected.containsAll(all)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectAllButton(allSelected, selected.any { it in all }, stringResource(if (allSelected) R.string.select_none else R.string.select_all)) {
                selected = if (allSelected) emptySet() else all
            }
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

fun startApply(items: List<SystemItem>) {
    val system = Env.system()
    Jobs.start(Env.app, R.string.job_system_restore, items, ::plainTitle, retry = ::startApply) { item, task ->
        task.progress(Stage.SETTINGS)
        system.apply(item)
        emptyList()
    }
}
