package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.FileSetKind
import com.aybrkaknc.easyandroid.core.StoredFileSet
import com.aybrkaknc.easyandroid.core.newestFileSet
import com.aybrkaknc.easyandroid.core.parseTimestamp
import java.time.Instant

/** The kinds of backup on Home. Each has a sheet with everything that can be done with it. */
enum class QuickKind(@param:StringRes val title: Int) {
    APPS(R.string.chip_apps),
    MESSAGES(R.string.chip_messages),
    CALLS(R.string.chip_calls),
    FOLDERS(R.string.chip_folders),
    WALLPAPERS(R.string.chip_wallpapers),
    SYSTEM(R.string.tab_system),
}

/** What a choice in the sheet asks Home to do. The sheet closes first, so dialogs never stack on it. */
sealed interface QuickEvent {
    data class Action(val action: QuickAction) : QuickEvent
    data class Show(val segment: BackupsSegment) : QuickEvent
    data class Backup(val kind: FileSetKind) : QuickEvent
    data class Restore(val set: StoredFileSet) : QuickEvent
    data object Pick : QuickEvent
    data object BackupSystem : QuickEvent
    data object ShowSystem : QuickEvent
    data object EasyAdb : QuickEvent
}

// One color per group, so the three groups of a sheet can be told apart at a glance.
private val BACKUP_GROUP = Category.BACKUP
private val RESTORE_GROUP = Category.MESSAGES
private val CONNECT_GROUP = Category.SYSTEM

private val APP_BACKUPS = listOf(QuickAction.BACKUP_ALL, QuickAction.BACKUP_PENDING, QuickAction.BACKUP_UPDATED, QuickAction.BACKUP_REDO)
private val APP_RESTORES = listOf(QuickAction.RESTORE_ALL, QuickAction.RESTORE_MISSING, QuickAction.RESTORE_NEWER)

private fun QuickKind.files(): Array<FileSetKind> = when (this) {
    QuickKind.MESSAGES -> arrayOf(FileSetKind.MESSAGES)
    QuickKind.CALLS -> arrayOf(FileSetKind.CALLS)
    QuickKind.FOLDERS -> arrayOf(FileSetKind.FOLDERS, FileSetKind.MEDIA)
    QuickKind.WALLPAPERS -> arrayOf(FileSetKind.WALLPAPER)
    else -> emptyArray()
}

private fun QuickKind.segment(): BackupsSegment = when (this) {
    QuickKind.MESSAGES -> BackupsSegment.MESSAGES
    QuickKind.CALLS -> BackupsSegment.CALLS
    QuickKind.FOLDERS -> BackupsSegment.FOLDERS
    QuickKind.WALLPAPERS -> BackupsSegment.WALLPAPERS
    else -> BackupsSegment.APPS
}

/** A card with a colored heading and an icon. The rows inside are the choices of that group. */
@Composable
private fun QuickGroup(@StringRes title: Int, color: Color, icon: Painter, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp)).background(cardColor()).padding(vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = color)
        }
        content()
    }
}

@Composable
private fun QuickRow(title: String, enabled: Boolean, summary: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    SettingRow(
        title = title, summary = summary, enabled = enabled, onClick = onClick,
        titleColor = if (danger) MaterialTheme.colorScheme.error else null,
    )
}

/**
 * Everything that can be done with one kind of backup: make one, restore the newest, open the list.
 * Apps and system settings have their own actions. Choices that would do nothing are dimmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSheet(kind: QuickKind, model: MainViewModel, enabled: Boolean, onDismiss: () -> Unit, onEvent: (QuickEvent) -> Unit) {
    val stored by model.backups.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val pc by model.pcBackups.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val formatter = rememberRelativeFormatter()
    val now = remember(stored) { Instant.now() }
    val list = stored
    val targets = remember(apps, list, pc) {
        val installed = apps
        if (installed == null || list == null) null else QuickTargets(installed, list.backups, pc, Env.debloat()::isProtected)
    }

    fun choose(event: QuickEvent) {
        onDismiss()
        onEvent(event)
    }

    fun count(n: Int) = resources.getQuantityString(R.plurals.quick_backup_count, n, n)

    fun can(action: QuickAction): Boolean {
        val found = targets ?: return false
        return when (action) {
            QuickAction.RESTORE_ALL, QuickAction.RESTORE_MISSING, QuickAction.RESTORE_NEWER -> found.restore(action).isNotEmpty()
            QuickAction.DELETE_MISSING -> found.deletable().isNotEmpty()
            else -> found.backup(action).isNotEmpty()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(kind.title), Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            )
            when (kind) {
                QuickKind.APPS -> {
                    QuickGroup(R.string.quick_group_backup, BACKUP_GROUP, painterResource(R.drawable.ic_save)) {
                        APP_BACKUPS.forEach { QuickRow(stringResource(it.title), enabled && can(it)) { choose(QuickEvent.Action(it)) } }
                    }
                    QuickGroup(R.string.quick_group_restore, RESTORE_GROUP, painterResource(R.drawable.ic_restore)) {
                        APP_RESTORES.forEach { QuickRow(stringResource(it.title), enabled && can(it)) { choose(QuickEvent.Action(it)) } }
                        QuickRow(stringResource(R.string.quick_show_backups), enabled && list?.backups?.isNotEmpty() == true) {
                            choose(QuickEvent.Show(BackupsSegment.APPS))
                        }
                        QuickRow(
                            stringResource(QuickAction.DELETE_MISSING.title), enabled && can(QuickAction.DELETE_MISSING), danger = true,
                        ) { choose(QuickEvent.Action(QuickAction.DELETE_MISSING)) }
                    }
                }
                QuickKind.SYSTEM -> {
                    val saved = list?.systemSnapshots?.size ?: 0
                    QuickGroup(R.string.quick_group_backup, BACKUP_GROUP, painterResource(R.drawable.ic_save)) {
                        QuickRow(stringResource(R.string.action_system_backup), enabled) { choose(QuickEvent.BackupSystem) }
                    }
                    QuickGroup(R.string.quick_group_restore, RESTORE_GROUP, painterResource(R.drawable.ic_restore)) {
                        QuickRow(
                            stringResource(R.string.quick_show_backups), enabled && saved > 0, summary = if (saved > 0) count(saved) else null,
                        ) { choose(QuickEvent.ShowSystem) }
                    }
                }
                else -> {
                    val kinds = kind.files()
                    val newest = list?.newestFileSet(*kinds)
                    val saved = list?.fileSets.orEmpty().count { it.set.kind in kinds }
                    val ago = newest?.let { parseTimestamp(it.set.createdAt)?.toInstant() }?.let { formatter.since(it, now) }
                    QuickGroup(R.string.quick_group_backup, BACKUP_GROUP, painterResource(R.drawable.ic_save)) {
                        if (kind == QuickKind.FOLDERS) {
                            QuickRow(stringResource(R.string.fileset_pick), enabled) { choose(QuickEvent.Pick) }
                        } else {
                            QuickRow(stringResource(R.string.fileset_backup), enabled) { choose(QuickEvent.Backup(kinds[0])) }
                        }
                    }
                    QuickGroup(R.string.quick_group_restore, RESTORE_GROUP, painterResource(R.drawable.ic_restore)) {
                        QuickRow(
                            stringResource(R.string.quick_restore_latest), enabled && newest != null,
                            summary = if (newest != null && ago != null) "$ago, ${formatSize(newest.set.totalBytes)}" else null,
                        ) { newest?.let { choose(QuickEvent.Restore(it)) } }
                        QuickRow(
                            stringResource(R.string.quick_show_backups), enabled && saved > 0, summary = if (saved > 0) count(saved) else null,
                        ) { choose(QuickEvent.Show(kind.segment())) }
                    }
                }
            }
            QuickGroup(R.string.quick_group_connect, CONNECT_GROUP, rememberVectorPainter(Icons.Default.Send)) {
                QuickRow(stringResource(R.string.easyadb_title), enabled) { choose(QuickEvent.EasyAdb) }
            }
        }
    }
}
