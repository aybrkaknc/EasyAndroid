package com.aybrkaknc.easyandroid.ui

import android.os.Environment
import android.os.StatFs
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.RootState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Granted = Color(0xFF2EB67D)

/** Overview of the phone and the quick actions, grouped like the things they back up. */
@Composable
fun HomeScreen(
    model: MainViewModel,
    ready: Boolean,
    scroll: ScrollState,
    onAction: (QuickAction) -> Unit,
    onMore: () -> Unit,
    onEasyAdb: () -> Unit,
    onSystem: () -> Unit,
    onSystemRestore: () -> Unit,
    onStorage: () -> Unit,
) {
    val root by model.root.collectAsStateWithLifecycle()
    val settings by Env.prefs.state.collectAsStateWithLifecycle()
    val granted = root == RootState.GRANTED
    val scope = rememberCoroutineScope()
    var appsY by remember { mutableIntStateOf(0) }
    var systemY by remember { mutableIntStateOf(0) }
    val android = painterResource(R.drawable.ic_android)

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
        CapsLabel(stringResource(R.string.home_overview), Modifier.padding(start = 12.dp, top = 16.dp, bottom = 12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardColor())
                .padding(vertical = 8.dp),
        ) {
            Storage(onStorage)
            Line()
            RootStatus(model, root)
            Line()
            if (granted) {
                CapsLabel(stringResource(R.string.home_wireless), Modifier.padding(start = 16.dp, top = 12.dp))
                WirelessControls(model)
                Line()
            }
            Chips(
                onApps = { scope.launch { scroll.animateScrollTo(appsY) } },
                onSystemSection = { scope.launch { scroll.animateScrollTo(systemY) } },
                onWifi = { if (granted) onSystem() },
            )
        }

        Column(Modifier.onGloballyPositioned { appsY = it.positionInParent().y.roundToInt() }) {
            SectionLabel(android, stringResource(R.string.section_apps))
            val pinned = QuickAction.entries.filter { it.id in settings.pinned }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pinned.forEachIndexed { index, action ->
                    QuickActionCard(action, groupShape(index, pinned.size + 1), ready, null, onAction, onEasyAdb)
                }
                Row(
                    Modifier.fillMaxWidth().clip(groupShape(pinned.size, pinned.size + 1))
                        .background(cardColor()).clickable(onClick = onMore).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.more_actions), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Column(Modifier.onGloballyPositioned { systemY = it.positionInParent().y.roundToInt() }) {
            SectionLabel(rememberVectorPainter(Icons.Default.Build), stringResource(R.string.section_system))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                QuickCard(
                    stringResource(R.string.action_system_backup), stringResource(R.string.action_system_backup_text),
                    groupShape(0, 2), enabled = granted, onClick = onSystem,
                )
                QuickCard(
                    stringResource(R.string.action_system_restore), stringResource(R.string.action_system_restore_text),
                    groupShape(1, 2), enabled = granted, root = true,
                    buttons = listOf(CardButton(R.string.from_device, onSystemRestore), CardButton(R.string.with_easyadb, onEasyAdb)),
                )
            }
        }

        SoonSection(
            painterResource(R.drawable.ic_message), R.string.section_messages,
            R.string.action_messages_backup, R.string.action_messages_backup_text,
            R.string.action_messages_restore, R.string.action_messages_restore_text,
        )
        SoonSection(
            rememberVectorPainter(Icons.Default.Call), R.string.section_calls,
            R.string.action_calls_backup, R.string.action_calls_backup_text,
            R.string.action_calls_restore, R.string.action_calls_restore_text,
        )
        SoonSection(
            painterResource(R.drawable.ic_folder), R.string.section_folders,
            R.string.action_folders_backup, R.string.action_folders_backup_text,
            R.string.action_folders_restore, R.string.action_folders_restore_text,
        )
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
    val version by model.rootVersion.collectAsStateWithLifecycle()
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
            version?.let { if (root == RootState.GRANTED) Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

@Composable
private fun Chips(onApps: () -> Unit, onSystemSection: () -> Unit, onWifi: () -> Unit) {
    val context = LocalContext.current
    val soon: () -> Unit = { showSoon(context) }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryChip(painterResource(R.drawable.ic_android), stringResource(R.string.chip_apps), Category.BACKUP, onClick = onApps)
        CategoryChip(painterResource(R.drawable.ic_message), stringResource(R.string.chip_messages), Category.MESSAGES, soon = true, onClick = soon)
        CategoryChip(rememberVectorPainter(Icons.Default.Call), stringResource(R.string.chip_calls), Category.CALLS, soon = true, onClick = soon)
        CategoryChip(painterResource(R.drawable.ic_folder), stringResource(R.string.chip_folders), Category.FOLDERS, soon = true, onClick = soon)
        CategoryChip(painterResource(R.drawable.ic_wallpaper), stringResource(R.string.chip_wallpapers), Category.WALLPAPERS, soon = true, onClick = soon)
        CategoryChip(painterResource(R.drawable.ic_wifi), stringResource(R.string.chip_wifi), Category.WIFI, onClick = onWifi)
        CategoryChip(rememberVectorPainter(Icons.Default.Build), stringResource(R.string.tab_system), Category.SYSTEM, onClick = onSystemSection)
    }
}

/** A section of a feature that does not exist yet. */
@Composable
private fun SoonSection(
    icon: Painter,
    @StringRes title: Int,
    @StringRes backup: Int,
    @StringRes backupText: Int,
    @StringRes restore: Int,
    @StringRes restoreText: Int,
) {
    val context = LocalContext.current
    val soon: () -> Unit = { showSoon(context) }
    SectionLabel(icon, stringResource(title))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        QuickCard(stringResource(backup), stringResource(backupText), groupShape(0, 2), soon = true, onClick = soon)
        QuickCard(
            stringResource(restore), stringResource(restoreText), groupShape(1, 2), soon = true, onClick = soon,
            buttons = listOf(CardButton(R.string.from_device, soon), CardButton(R.string.with_easyadb, soon)),
        )
    }
}
