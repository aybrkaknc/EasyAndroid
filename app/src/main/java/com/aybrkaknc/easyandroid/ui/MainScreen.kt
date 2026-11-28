package com.aybrkaknc.easyandroid.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.RootState
import com.aybrkaknc.easyandroid.job.JobKind
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.PcApproval
import com.aybrkaknc.easyandroid.job.jobKind

private enum class Tab(@param:StringRes val title: Int) {
    HOME(R.string.tab_home), BACKUPS(R.string.tab_backups), DEBLOAT(R.string.tab_debloat), SETTINGS(R.string.tab_settings),
}

@Composable
private fun Tab.icon(): Painter = when (this) {
    Tab.HOME -> rememberVectorPainter(Icons.Default.Home)
    Tab.BACKUPS -> painterResource(R.drawable.ic_restore)
    Tab.DEBLOAT -> rememberVectorPainter(Icons.Default.Delete)
    Tab.SETTINGS -> rememberVectorPainter(Icons.Default.Settings)
}

@Composable
fun MainScreen(model: MainViewModel) {
    val root by model.root.collectAsStateWithLifecycle()
    val job by Jobs.state.collectAsStateWithLifecycle()
    // Files that arrive have a window of their own; every other job takes the whole page.
    val receiving = job?.takeIf { jobKind(it.title) == JobKind.RECEIVE }
    val page = job.takeIf { receiving == null }
    val error by model.error.collectAsStateWithLifecycle()
    val offer by model.deleteOffer.collectAsStateWithLifecycle()
    val question by PcApproval.question.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showApps by rememberSaveable { mutableStateOf(false) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showEasyAdb by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var request by remember { mutableStateOf<BackupsRequest?>(null) }
    // The apps a quick action has ticked in the app list.
    var preselect by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleting by remember { mutableStateOf<List<String>?>(null) }
    val apps by model.apps.collectAsStateWithLifecycle()
    val stored by model.backups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Kept here so that Home stays where it was behind a full screen list.
    val homeScroll = rememberScrollState()

    fun perform(action: QuickAction) {
        val targets = QuickTargets(apps ?: return, stored?.backups ?: return, model.pcBackups.value, Env.debloat()::isProtected)
        fun <T> check(items: List<T>, use: (List<T>) -> Unit) {
            if (items.isEmpty()) Toast.makeText(context, R.string.nothing_to_do, Toast.LENGTH_SHORT).show() else use(items)
        }
        when (action) {
            // Back up apps opens the list with nothing ticked; the other choices tick the apps they cover.
            QuickAction.BACKUP_ALL -> {
                preselect = emptySet()
                showApps = true
            }
            QuickAction.BACKUP_PENDING, QuickAction.BACKUP_UPDATED, QuickAction.BACKUP_REDO ->
                check(targets.backup(action)) { found ->
                    preselect = found.map { it.packageName }.toSet()
                    showApps = true
                }
            QuickAction.RESTORE_ALL, QuickAction.RESTORE_MISSING, QuickAction.RESTORE_NEWER -> check(targets.restore(action)) { found ->
                request = BackupsRequest.Select(found.map { it.id }.toSet())
                tab = Tab.BACKUPS
            }
            QuickAction.DELETE_MISSING -> check(targets.deletable()) { found -> deleting = found.map { it.id } }
        }
    }

    fun openSegment(segment: BackupsSegment) {
        request = BackupsRequest.Open(segment)
        showSearch = false
        tab = Tab.BACKUPS
    }

    // Back from another tab goes to the first one; only back from the first tab leaves the app.
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = showApps) { showApps = false }
    BackHandler(enabled = settingsPage != null) { settingsPage = null }
    BackHandler(enabled = showSystem) { showSystem = false }

    Scaffold(
        bottomBar = {
            val navigation = page == null && !showApps && !showSystem && !showSearch && !(tab == Tab.SETTINGS && settingsPage != null)
            Column {
                receiving?.takeIf { it.hidden }?.let {
                    // Without the navigation bar the strip is the lowest thing and keeps clear of the system's.
                    ReceiveStrip(it, if (navigation) Modifier else Modifier.navigationBarsPadding())
                }
                if (navigation) {
                    NavigationBar {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it,
                                onClick = { tab = it },
                                icon = { Icon(it.icon(), contentDescription = null) },
                                label = { Text(stringResource(it.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = page
            when {
                current != null -> JobScreen(current)
                showApps -> Overlay(stringResource(R.string.home_quick_backup), { showApps = false }) { AppsScreen(model, preselect) }
                showSystem -> Overlay(stringResource(R.string.home_quick_system), { showSystem = false }) {
                    SystemBackupScreen(model) { showSystem = false }
                }
                showSearch -> Overlay(stringResource(R.string.search), { showSearch = false }) {
                    SearchScreen(
                        model,
                        onApps = {
                            showSearch = false
                            preselect = emptySet()
                            showApps = true
                        },
                        onAction = {
                            showSearch = false
                            perform(it)
                        },
                    )
                }
                tab == Tab.SETTINGS -> {
                    val page = settingsPage
                    if (page == null) {
                        SettingsScreen(model, onPage = { settingsPage = it })
                    } else {
                        Overlay(stringResource(page.title), { settingsPage = null }) { SettingsPageContent(page, model) }
                    }
                }
                tab == Tab.HOME -> HomeScreen(
                    model,
                    homeScroll,
                    onAction = ::perform,
                    onEasyAdb = { showEasyAdb = true },
                    onSystemRestore = {
                        request = BackupsRequest.System
                        tab = Tab.BACKUPS
                    },
                    onSystem = { showSystem = true },
                    onStorage = {
                        settingsPage = SettingsPage.STORAGE
                        tab = Tab.SETTINGS
                    },
                    onSearch = { showSearch = true },
                    onSegment = ::openSegment,
                )
                root == RootState.CHECKING -> Loading()
                root == RootState.MISSING -> NoRoot(model::checkRoot)
                else -> when (tab) {
                    Tab.BACKUPS -> BackupsScreen(model, request) { request = null }
                    else -> DebloatScreen(model)
                }
            }
        }
    }

    if (showEasyAdb) WirelessSheet(model, easyAdb = true) { showEasyAdb = false }

    // The PC checked its copy, so the one on the phone can go. Never without asking.
    if (offer.isNotEmpty() && page == null) {
        ConfirmDialog(
            title = stringResource(R.string.pc_delete_title),
            confirm = stringResource(R.string.delete),
            dismiss = stringResource(R.string.pc_keep),
            onConfirm = {
                val ids = offer
                model.deleteOffer.value = emptyList()
                model.run {
                    val store = Env.store()
                    ids.forEach(store::delete)
                    model.refreshBackups()
                }
            },
            onDismiss = { model.deleteOffer.value = emptyList() },
        ) { Text(stringResource(R.string.pc_delete_text, offer.size), color = MaterialTheme.colorScheme.error) }
    }

    deleting?.let { ids ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, ids.size),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                deleting = null
                model.run {
                    val store = Env.store()
                    ids.forEach(store::delete)
                    model.refreshBackups()
                }
            },
            onDismiss = { deleting = null },
        ) {
            Text(stringResource(R.string.delete_warning), color = MaterialTheme.colorScheme.error)
            ids.forEach { Hint(it) }
        }
    }

    receiving?.takeIf { !it.hidden }?.let { ReceiveDialog(it) }

    question?.let { PcQuestionDialog(it) }

    error?.let { text ->
        ConfirmDialog(
            title = stringResource(R.string.error),
            confirm = stringResource(R.string.ok),
            onConfirm = { model.error.value = null },
            onDismiss = { model.error.value = null },
        ) { Text(text.resolve(LocalContext.current)) }
    }
}

/** A screen shown full screen over the tabs, with a back arrow. */
@Composable
private fun Overlay(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun NoRoot(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.no_root_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.no_root_text), textAlign = TextAlign.Center)
        Button(onRetry) { Text(stringResource(R.string.retry)) }
    }
}
