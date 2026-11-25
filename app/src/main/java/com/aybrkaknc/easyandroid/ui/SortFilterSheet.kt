package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.R

@StringRes
private fun AppSort.title(): Int = when (this) {
    AppSort.NAME -> R.string.sort_name
    AppSort.INSTALL -> R.string.sort_install
    AppSort.UPDATE -> R.string.sort_update
    AppSort.BACKUP_DATE -> R.string.sort_backup_date
    AppSort.BACKUP_SIZE -> R.string.sort_backup_size
}

@Composable
private fun SortCircle(sort: AppSort, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp).size(width = 84.dp, height = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            val painter: Painter? = when (sort) {
                AppSort.NAME -> null
                AppSort.INSTALL -> rememberVectorPainter(Icons.Default.Add)
                AppSort.UPDATE -> painterResource(R.drawable.ic_restart)
                AppSort.BACKUP_DATE -> rememberVectorPainter(Icons.Default.Check)
                AppSort.BACKUP_SIZE -> painterResource(R.drawable.ic_donut)
            }
            if (painter == null) Text("AZ", color = tint, style = MaterialTheme.typography.titleMedium)
            else Icon(painter, null, tint = tint)
        }
        Text(
            stringResource(sort.title()), Modifier.padding(top = 8.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FilterGroup(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** Sorting and the enabled filter of the app list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterSheet(sort: AppSort, filter: EnabledFilter, onDismiss: () -> Unit, onApply: (AppSort, EnabledFilter) -> Unit) {
    var pendingSort by remember { mutableStateOf(sort) }
    var pendingFilter by remember { mutableStateOf(filter) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                Box(Modifier.weight(1f))
                Button({ onApply(pendingSort, pendingFilter) }) { Text(stringResource(R.string.sheet_apply)) }
            }
            Text(stringResource(R.string.sort_title), Modifier.padding(top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                AppSort.entries.forEach { SortCircle(it, it == pendingSort) { pendingSort = it } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                OutlinedButton({
                    pendingSort = AppSort.NAME
                    pendingFilter = EnabledFilter.ALL
                }) { Text(stringResource(R.string.sheet_reset), color = MaterialTheme.colorScheme.error) }
            }
            FilterGroup(R.string.filter_enabled) {
                FilterChip(pendingFilter == EnabledFilter.ALL, { pendingFilter = EnabledFilter.ALL }, { Text(stringResource(R.string.all)) })
                FilterChip(pendingFilter == EnabledFilter.ENABLED, { pendingFilter = EnabledFilter.ENABLED }, { Text(stringResource(R.string.filter_enabled_on)) })
                FilterChip(pendingFilter == EnabledFilter.DISABLED, { pendingFilter = EnabledFilter.DISABLED }, { Text(stringResource(R.string.filter_enabled_off)) })
            }
        }
    }
}
