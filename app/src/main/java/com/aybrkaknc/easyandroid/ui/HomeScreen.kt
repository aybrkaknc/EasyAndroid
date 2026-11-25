package com.aybrkaknc.easyandroid.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.RootState
import com.aybrkaknc.easyandroid.core.FileSetKind
import com.aybrkaknc.easyandroid.core.StoredFileSet
import com.aybrkaknc.easyandroid.core.parseTimestamp
import java.time.Instant

val Granted = Color(0xFF2EB67D)

/** Overview of the phone, then one line for each kind of backup that opens everything that can be done with it. */
@Composable
fun HomeScreen(
    model: MainViewModel,
    scroll: ScrollState,
    onAction: (QuickAction) -> Unit,
    onEasyAdb: () -> Unit,
    onSystem: () -> Unit,
    onSystemRestore: () -> Unit,
    onStorage: () -> Unit,
    onSearch: () -> Unit,
    onSegment: (BackupsSegment) -> Unit,
) {
    val root by model.root.collectAsStateWithLifecycle()
    val granted = root == RootState.GRANTED
    var sheet by rememberSaveable { mutableStateOf<QuickKind?>(null) }
    var backing by remember { mutableStateOf<FileSetKind?>(null) }
    var picking by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<StoredFileSet?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CapsLabel(stringResource(R.string.home_overview), Modifier.weight(1f))
            IconButton(onSearch) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardColor())
                .padding(vertical = 8.dp),
        ) {
            Storage(onStorage)
            Line()
            RootStatus(model, root)
            if (granted) {
                Line()
                WirelessCard(model)
            }
        }

        BackupList(model, granted) { sheet = it }
    }

    sheet?.let { kind ->
        QuickSheet(kind, model, granted, onDismiss = { sheet = null }) { event ->
            when (event) {
                is QuickEvent.Action -> onAction(event.action)
                is QuickEvent.Show -> onSegment(event.segment)
                is QuickEvent.Backup -> backing = event.kind
                is QuickEvent.Restore -> restoring = event.set
                QuickEvent.Pick -> picking = true
                QuickEvent.BackupSystem -> onSystem()
                QuickEvent.ShowSystem -> onSystemRestore()
                QuickEvent.EasyAdb -> onEasyAdb()
            }
        }
    }
    backing?.let { kind ->
        FileSetBackupDialog(kind, onDismiss = { backing = null }) { target ->
            backing = null
            startFileBackup(model, kind, emptyList(), target)
        }
    }
    if (picking) {
        FolderPickerDialog(emptySet(), onDismiss = { picking = false }) { names, target, direct ->
            picking = false
            startFileBackup(model, FileSetKind.FOLDERS, names, target, direct)
        }
    }
    restoring?.let { item ->
        FileSetRestoreDialog(item, onDismiss = { restoring = null }) {
            restoring = null
            startFileRestore(model, item)
        }
    }
}

@Composable
private fun Line() = HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

@Composable
private fun Storage(onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val stat = remember { StatFs(Environment.getExternalStorageDirectory().path) }
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = if (total > 0) (total - free).toFloat() / total else 0f
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CapsLabel(stringResource(R.string.home_storage))
                Row {
                    Text(
                        "%.2f GB".format(locale, free / 1e9), Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.home_free_of, formatSize(total)), Modifier.alignByBaseline().padding(start = 6.dp),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalIconButton(onClick) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.backup_folder))
            }
        }
        LinearProgressIndicator(progress = { used }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        Text(
            stringResource(R.string.home_used, "%.1f".format(locale, used * 100)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RootStatus(model: MainViewModel, root: RootState) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            CapsLabel(stringResource(R.string.home_root))
            Text(
                stringResource(
                    when (root) {
                        RootState.GRANTED -> R.string.home_root_granted
                        RootState.MISSING -> R.string.home_root_missing
                        RootState.CHECKING -> R.string.home_root_checking
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                color = when (root) {
                    RootState.GRANTED -> Granted
                    RootState.MISSING -> MaterialTheme.colorScheme.error
                    RootState.CHECKING -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (root == RootState.MISSING) Hint(stringResource(R.string.no_root_text))
        }
        when (root) {
            RootState.GRANTED -> Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_verified), null, Modifier.size(24.dp), tint = Granted) }
            RootState.MISSING -> TextButton(model::checkRoot) { Text(stringResource(R.string.retry)) }
            RootState.CHECKING -> {}
        }
    }
}

private class BackupEntry(val kind: QuickKind, val icon: Painter, val tint: Color, val times: List<Instant>)

/** One line for each kind of backup: how many there are and how long ago the last one was made. */
@Composable
private fun BackupList(model: MainViewModel, enabled: Boolean, onOpen: (QuickKind) -> Unit) {
    val stored by model.backups.collectAsStateWithLifecycle()
    val pc by model.pcBackups.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val formatter = rememberRelativeFormatter()
    val now = remember(stored) { Instant.now() }
    val list = stored

    fun files(vararg kinds: FileSetKind) = list?.fileSets.orEmpty().filter { it.set.kind in kinds }
        .mapNotNull { parseTimestamp(it.set.createdAt)?.toInstant() }
    val entries = listOf(
        BackupEntry(
            QuickKind.APPS, painterResource(R.drawable.ic_android), Category.BACKUP,
            list?.backups.orEmpty().mapNotNull { parseTimestamp(it.manifest.createdAt)?.toInstant() } +
                pc.mapNotNull { parseTimestamp(it.createdAt)?.toInstant() },
        ),
        BackupEntry(QuickKind.MESSAGES, painterResource(R.drawable.ic_message), Category.MESSAGES, files(FileSetKind.MESSAGES)),
        BackupEntry(QuickKind.CALLS, rememberVectorPainter(Icons.Default.Call), Category.CALLS, files(FileSetKind.CALLS)),
        BackupEntry(QuickKind.FOLDERS, painterResource(R.drawable.ic_folder), Category.FOLDERS, files(FileSetKind.FOLDERS, FileSetKind.MEDIA)),
        BackupEntry(QuickKind.WALLPAPERS, painterResource(R.drawable.ic_wallpaper), Category.WALLPAPERS, files(FileSetKind.WALLPAPER)),
        BackupEntry(QuickKind.SYSTEM, rememberVectorPainter(Icons.Default.Build), Category.SYSTEM, list?.systemSnapshots.orEmpty().mapNotNull(::systemBackupTime)),
    )

    CapsLabel(stringResource(R.string.home_backups), Modifier.padding(start = 12.dp, top = 24.dp, bottom = 12.dp))
    CardGroup(entries.size, horizontal = 0.dp) { index, shape ->
        val entry = entries[index]
        val last = entry.times.maxOrNull()
        SettingCard(
            stringResource(entry.kind.title), shape,
            summary = when {
                list == null -> null
                last == null -> stringResource(R.string.home_backups_none)
                else -> resources.getQuantityString(R.plurals.home_backups_line, entry.times.size, entry.times.size, formatter.since(last, now))
            },
            icon = entry.icon, tint = entry.tint, enabled = enabled,
            summaryColor = if (last != null && isStale(last, now)) Amber else null,
            onClick = { onOpen(entry.kind) },
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
    }
}
