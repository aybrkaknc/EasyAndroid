package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.BackupOptions

private class ChipSpec(
    @param:StringRes val text: Int,
    val selected: Boolean,
    val enabled: Boolean = true,
    val soon: Boolean = false,
    val onClick: () -> Unit = {},
)

@Composable
private fun SheetChip(spec: ChipSpec, modifier: Modifier) {
    val context = LocalContext.current
    FilterChip(
        spec.selected,
        { if (spec.soon) showSoon(context) else spec.onClick() },
        { Text(stringResource(spec.text), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier.heightIn(min = 52.dp).alpha(if (spec.soon) 0.4f else 1f),
        enabled = spec.enabled,
        leadingIcon = if (spec.selected) ({ Icon(Icons.Default.Check, null) }) else null,
        shape = RoundedCornerShape(26.dp),
    )
}

/** Chips two to a row, like the sheet they follow. */
@Composable
private fun ChipRows(vararg chips: ChipSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        chips.toList().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetChip(pair[0], Modifier.weight(1f))
                if (pair.size > 1) SheetChip(pair[1], Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** What goes into a backup and where it goes. Media and Cloud are not there yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(count: Int, onDismiss: () -> Unit, note: String? = null, onConfirm: (BackupOptions) -> Unit) {
    var options by remember { mutableStateOf(Env.prefs.state.value.backup) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.back_up_count, count), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.sheet_parts), Modifier.padding(top = 16.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium)
            ChipRows(
                ChipSpec(R.string.part_apk, selected = true, enabled = false),
                ChipSpec(R.string.part_data, options.data) { options = options.copy(data = !options.data) },
                ChipSpec(R.string.part_external, options.externalData) { options = options.copy(externalData = !options.externalData) },
                ChipSpec(R.string.part_expansion, options.obb) { options = options.copy(obb = !options.obb) },
                ChipSpec(R.string.part_media, selected = false, soon = true),
            )
            Text(stringResource(R.string.compression), Modifier.padding(top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium)
            ChipRows(
                ChipSpec(R.string.compression_none, !options.compress) { options = options.copy(compress = false) },
                ChipSpec(R.string.compression_zstd, options.compress) { options = options.copy(compress = true) },
            )
            Text(stringResource(R.string.sheet_locations), Modifier.padding(top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium)
            ChipRows(
                ChipSpec(R.string.location_device, selected = true),
                ChipSpec(R.string.location_cloud, selected = false, soon = true),
            )
            if (options.data || options.externalData) {
                Text(stringResource(R.string.force_stop_warning), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
                if (note != null) Text(note, color = MaterialTheme.colorScheme.error)
            }
            Button({ onConfirm(options) }, Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.sheet_backup), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
