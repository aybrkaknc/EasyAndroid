package com.aybrkaknc.easyandroid.ui

import com.aybrkaknc.easyandroid.job.ItemInfo
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.AppInfo
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.DebloatAction
import com.aybrkaknc.easyandroid.core.DebloatProfile
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.ProfileEntry
import com.aybrkaknc.easyandroid.core.Removal
import com.aybrkaknc.easyandroid.core.Sh
import com.aybrkaknc.easyandroid.core.Stage
import com.aybrkaknc.easyandroid.core.timestamp
import com.aybrkaknc.easyandroid.job.Jobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

private fun removalId(removal: Removal?): Int = when (removal) {
    Removal.RECOMMENDED -> R.string.removal_recommended
    Removal.ADVANCED -> R.string.removal_advanced
    Removal.EXPERT -> R.string.removal_expert
    Removal.UNSAFE -> R.string.removal_unsafe
    null -> R.string.removal_unlisted
}

internal val AppInfo.active get() = installed && enabled

/** From safe to dangerous, so a glance at the list tells how careful to be. */
private fun removalColor(removal: Removal?): Color = when (removal) {
    Removal.RECOMMENDED -> Color(0xFF2EB67D)
    Removal.ADVANCED -> Color(0xFFE5B83A)
    Removal.EXPERT -> Color(0xFFF28C38)
    Removal.UNSAFE -> Color(0xFFFF5A5F)
    null -> Color(0xFF9AA0A6)
}

@Composable
private fun ColorChip(selected: Boolean, color: Color, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected, onClick, label,
        leadingIcon = { Box(Modifier.size(8.dp).background(color, CircleShape)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.25f), selectedLabelColor = color),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = color.copy(alpha = 0.4f), selectedBorderColor = color,
        ),
    )
}

@Composable
fun DebloatScreen(model: MainViewModel) {
    val apps by model.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<Removal?>(Removal.RECOMMENDED) }
    var everything by rememberSaveable { mutableStateOf(false) }
    var removed by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var plan by remember { mutableStateOf<List<ProfileEntry>?>(null) }
    var info by remember { mutableStateOf<AppInfo?>(null) }

    var menu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val entries = removedProfile(model.apps.value.orEmpty())
        if (uri != null) model.run { saveProfile(uri, entries) }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.run {
            val text = Env.app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw AppException(ErrorCode.NOT_FOUND, uri.toString())
            val known = model.apps.value.orEmpty().map { it.packageName }.toSet()
            // Packages the new ROM does not have are left out.
            val entries = DebloatProfile.parse(text).entries.filter { it.packageName in known }
            withContext(Dispatchers.Main) { plan = entries }
        }
    }

    val all = apps ?: return Loading()
    val chosen = all.filter { it.packageName in selected }
    val debloat = remember { Env.debloat() }
    val visible = remember(all, query, level, everything, removed) {
        all.filter { it.active != removed }
            .filter { everything || it.debloat?.removal == level }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        val visibleNames = visible.map { it.packageName }.toSet()
        val allVisible = visibleNames.isNotEmpty() && selected.containsAll(visibleNames)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorChip(removed, Category.SYSTEM, { removed = !removed; selected = emptySet() }) { Text(stringResource(R.string.show_removed)) }
            FilterChip(everything, { everything = true }, { Text(stringResource(R.string.all)) })
            (Removal.entries + null).forEach { item ->
                ColorChip(!everything && level == item, removalColor(item), { level = item; everything = false }) {
                    Text(stringResource(removalId(item)))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.selected_count, chosen.size), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({
                searching = !searching
                if (!searching) query = ""
            }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
            SelectAllButton(allVisible, visible.any { it.packageName in selected }, stringResource(if (allVisible) R.string.select_none else R.string.select_all)) {
                selected = if (allVisible) selected - visibleNames else selected + visibleNames
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_save_state)) },
                        onClick = {
                            menu = false
                            save.launch("easyandroid-debloat.json")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_load)) },
                        onClick = {
                            menu = false
                            load.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
            }
        }
        if (searching) SearchField(query) { query = it }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.packageName }) { app ->
                val state = when {
                    !app.installed -> stringResource(R.string.state_uninstalled)
                    !app.enabled -> stringResource(R.string.state_disabled)
                    else -> ""
                }
                SelectRow(
                    packageName = app.packageName,
                    title = app.label,
                    subtitle = app.packageName,
                    detail = state,
                    checked = app.packageName in selected,
                    tint = removalColor(app.debloat?.removal),
                    enabled = !app.active || !debloat.isProtected(app.packageName),
                    onClick = { info = app },
                ) { selected = selected.toggle(app.packageName) }
            }
        }
        if (chosen.isNotEmpty()) {
            if (removed) {
                Button({
                    plan = chosen.map { ProfileEntry(it.packageName, if (it.installed) DebloatAction.ENABLE else DebloatAction.RESTORE) }
                }, Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.bring_back_count, chosen.size)) }
            } else {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ plan = chosen.map { ProfileEntry(it.packageName, DebloatAction.DISABLE) } }, Modifier.weight(1f)) {
                        Text(stringResource(R.string.debloat_disable_count, chosen.size))
                    }
                    Button({ plan = chosen.map { ProfileEntry(it.packageName, DebloatAction.UNINSTALL) } }, Modifier.weight(1f)) {
                        Text(stringResource(R.string.debloat_uninstall_count, chosen.size))
                    }
                }
            }
        }
    }

    info?.let { app ->
        ConfirmDialog(
            title = app.label,
            confirm = stringResource(R.string.close),
            onConfirm = { info = null },
            onDismiss = { info = null },
            dismiss = "",
        ) {
            SelectionContainer {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Heading(stringResource(removalId(app.debloat?.removal)))
                    Text(app.debloat?.description ?: stringResource(R.string.no_description))
                    val neededBy = app.debloat?.neededBy.orEmpty()
                    if (neededBy.isNotEmpty()) {
                        Heading(stringResource(R.string.needed_by))
                        neededBy.forEach { Hint(it) }
                    }
                }
            }
        }
    }

    plan?.let { entries ->
        DebloatPlanDialog(entries, all, onDismiss = { plan = null }) {
            plan = null
            selected = emptySet()
            startDebloat(model, all, entries)
        }
    }
}

/** Shows what will run and asks for confirmation. */
@Composable
fun DebloatPlanDialog(entries: List<ProfileEntry>, apps: List<AppInfo>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.debloat_confirm, entries.size),
        confirm = stringResource(R.string.apply),
        enabled = entries.isNotEmpty(),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        if (entries.any { it.action.removes }) {
            Text(stringResource(R.string.clear_warning), color = MaterialTheme.colorScheme.error)
        }
        val uninstalls = entries.filter { it.action == DebloatAction.UNINSTALL }.map { it.packageName }.toSet()
        if (uninstalls.isNotEmpty()) {
            Text(stringResource(R.string.uninstall_warning), color = MaterialTheme.colorScheme.error)
        }
        val userApps = apps.filter { !it.system && it.packageName in uninstalls }
        if (userApps.isNotEmpty()) {
            Text(stringResource(R.string.uninstall_user_warning, userApps.joinToString { it.label }), color = MaterialTheme.colorScheme.error)
        }
        Hint(stringResource(R.string.debloat_commands))
        entries.forEach {
            Text(it.action.command(it.packageName), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun startDebloat(model: MainViewModel, apps: List<AppInfo>, entries: List<ProfileEntry>) {
    val debloat = Env.debloat()
    val labels = apps.associate { it.packageName to it.label }
    Jobs.start(
        Env.app, R.string.job_debloat, entries, { labels[it.packageName] ?: it.packageName }, after = { model.refresh() },
        info = { ItemInfo(packageName = it.packageName) },
    ) { entry, task ->
        task.progress(Stage.DEBLOAT)
        debloat.apply(entry)
        emptyList()
    }
}

/** Packages that are disabled or removed now, to be applied again on another ROM. */
internal fun removedProfile(apps: List<AppInfo>): List<ProfileEntry> =
    apps.filter { !it.active && Sh.isPackage(it.packageName) }
        .map { ProfileEntry(it.packageName, if (it.installed) DebloatAction.DISABLE else DebloatAction.UNINSTALL) }

internal fun saveProfile(uri: Uri, entries: List<ProfileEntry>) {
    val profile = DebloatProfile(DebloatProfile.FORMAT, 1, Build.MODEL, timestamp(OffsetDateTime.now()), entries)
    val output = Env.app.contentResolver.openOutputStream(uri, "wt") ?: throw AppException(ErrorCode.NOT_FOUND, uri.toString())
    output.use { it.write(profile.encode().toByteArray()) }
}
