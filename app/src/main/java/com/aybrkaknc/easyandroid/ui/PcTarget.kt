package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Where a backup goes. The PC is EasyADB, reached through adb. */
enum class Target { PHONE, PC }

/** Whether EasyADB is looking at this phone, asked again every few seconds while the caller is shown. */
@Composable
fun rememberPcConnected(): Boolean {
    var connected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            connected = withContext(Dispatchers.IO) { runCatching { Env.pc().connected() }.getOrDefault(false) }
            delay(3000)
        }
    }
    return connected
}

@Composable
private fun TargetChip(text: Int, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier) {
    FilterChip(
        selected, onClick, { Text(stringResource(text), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, modifier,
        enabled = enabled,
    )
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Phone or PC. The PC can only be chosen while EasyADB is looking at the phone. With [direct] set,
 * folders can go straight to the PC or be backed up on the phone first.
 */
@Composable
fun TargetChips(
    target: Target,
    onChange: (Target) -> Unit,
    modifier: Modifier = Modifier,
    direct: Boolean? = null,
    onDirect: (Boolean) -> Unit = {},
) {
    val connected = rememberPcConnected()
    Column(modifier) {
        Text(stringResource(R.string.target_title), Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TargetChip(R.string.target_phone, target == Target.PHONE, onClick = { onChange(Target.PHONE) }, modifier = Modifier.weight(1f))
            TargetChip(
                R.string.target_pc, target == Target.PC, enabled = connected || target == Target.PC,
                onClick = { onChange(Target.PC) }, modifier = Modifier.weight(1f),
            )
        }
        if (target == Target.PC) {
            Note(if (connected) R.string.target_pc_hint else R.string.target_pc_missing)
            if (direct != null) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TargetChip(R.string.target_direct, direct, onClick = { onDirect(true) }, modifier = Modifier.weight(1f))
                    TargetChip(R.string.target_via_phone, !direct, onClick = { onDirect(false) }, modifier = Modifier.weight(1f))
                }
                Note(if (direct) R.string.target_direct_hint else R.string.target_via_phone_hint)
            }
        }
    }
}
