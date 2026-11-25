package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R

/** One place to find an app, a shortcut or a quick action. */
@Composable
fun SearchScreen(
    model: MainViewModel,
    onApps: () -> Unit,
    onSystemBackup: () -> Unit,
    onAction: (QuickAction) -> Unit,
    onSegment: (BackupsSegment) -> Unit,
) {
    val apps by model.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val matches = apps.orEmpty().filter {
        it.installed && !it.system && (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
    }.take(30)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        SearchField(query) { query = it }

        Box12 {
            CapsLabel(stringResource(R.string.search_shortcuts), Modifier.padding(bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryChip(painterResource(R.drawable.ic_android), stringResource(R.string.chip_apps), Category.BACKUP, onClick = onApps)
                CategoryChip(painterResource(R.drawable.ic_message), stringResource(R.string.chip_messages), Category.MESSAGES) { onSegment(BackupsSegment.MESSAGES) }
                CategoryChip(rememberVectorPainter(Icons.Default.Call), stringResource(R.string.chip_calls), Category.CALLS) { onSegment(BackupsSegment.CALLS) }
                CategoryChip(painterResource(R.drawable.ic_folder), stringResource(R.string.chip_folders), Category.FOLDERS) { onSegment(BackupsSegment.FOLDERS) }
                CategoryChip(painterResource(R.drawable.ic_wallpaper), stringResource(R.string.chip_wallpapers), Category.WALLPAPERS) { onSegment(BackupsSegment.WALLPAPERS) }
                CategoryChip(painterResource(R.drawable.ic_wifi), stringResource(R.string.chip_wifi), Category.WIFI, onClick = onSystemBackup)
                CategoryChip(rememberVectorPainter(Icons.Default.Build), stringResource(R.string.tab_system), Category.SYSTEM, onClick = onSystemBackup)
            }
        }

        Box12 {
            CapsLabel(stringResource(R.string.search_quick), Modifier.padding(bottom = 8.dp))
            QuickLine(R.string.action_backup_all, R.string.action_backup_all_text) { onAction(QuickAction.BACKUP_ALL) }
            QuickLine(R.string.action_restore_all, R.string.action_restore_all_text) { onAction(QuickAction.RESTORE_ALL) }
        }

        Box12 {
            CapsLabel(stringResource(R.string.search_apps), Modifier.padding(bottom = 8.dp))
            matches.forEach { app ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onApps).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** A rounded block like the cards of Home. */
@Composable
private fun Box12(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .background(cardColor()).padding(20.dp),
    ) { content() }
}

@Composable
private fun QuickLine(title: Int, text: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(painterResource(R.drawable.ic_android), null, tint = Category.BACKUP)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
