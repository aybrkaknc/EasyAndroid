package com.aybrkaknc.easyandroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.MainViewModel
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.WirelessInfo
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.SuRoot
import com.aybrkaknc.easyandroid.core.WirelessDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What wireless debugging is doing now. [address] is known once the port is found. */
class WirelessState(val enabled: Boolean, val ip: String?, val port: Int?, val connected: Boolean) {
    val address: String? get() = port?.let { p -> ip?.let { "$it:$p" } }
}

/** Polls the phone while the caller is on screen. */
@Composable
fun rememberWirelessState(): WirelessState {
    val context = LocalContext.current
    val enabled by produceState(WirelessInfo.isEnabled(context)) {
        while (true) {
            value = WirelessInfo.isEnabled(context)
            delay(1000)
        }
    }
    val ip by produceState<String?>(null, enabled) {
        while (true) {
            value = WirelessInfo.wifiAddress(context)
            delay(3000)
        }
    }
    val port by produceState<Int?>(null, enabled) {
        value = null
        while (enabled) {
            value = withContext(Dispatchers.IO) { runCatching { WirelessDebug(SuRoot).listeningPort() }.getOrNull() }
            delay(4000)
        }
    }
    // A new port means a new session, so the old answer is dropped until the next check.
    val connected by produceState(false, port) {
        value = false
        val p = port ?: return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { WirelessDebug(SuRoot).isConnected(p) }.getOrDefault(false) }
            delay(4000)
        }
    }
    return WirelessState(enabled, ip, port, connected)
}

@Composable
private fun WirelessState.line(): String {
    val address = address
    return when {
        !enabled -> stringResource(R.string.wireless_off)
        address != null -> stringResource(if (connected) R.string.wireless_line_connected else R.string.wireless_line_idle, address)
        ip != null -> stringResource(R.string.wireless_port_unknown, ip)
        else -> stringResource(R.string.wireless_no_wifi)
    }
}

/** The switch. Turning it off while a PC is connected cuts that connection, so it asks first. */
@Composable
private fun WirelessSwitch(model: MainViewModel, state: WirelessState) {
    var confirming by remember { mutableStateOf(false) }
    Switch(state.enabled, onCheckedChange = { on ->
        if (!on && state.connected) confirming = true else model.run { WirelessDebug(SuRoot).setEnabled(on) }
    })
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.wireless_off_title),
            confirm = stringResource(R.string.wireless_off_confirm),
            onConfirm = {
                confirming = false
                model.run { WirelessDebug(SuRoot).setEnabled(false) }
            },
            onDismiss = { confirming = false },
        ) { Text(stringResource(R.string.wireless_off_text)) }
    }
}

/** One line on Home, like Root status: the state on the left, the switch on the right. A tap opens the details. */
@Composable
fun WirelessCard(model: MainViewModel) {
    val state = rememberWirelessState()
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            CapsLabel(stringResource(R.string.home_wireless))
            Text(
                state.line(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    !state.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    state.connected -> Granted
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        WirelessSwitch(model, state)
    }
    if (open) WirelessSheet(model, easyAdb = false) { open = false }
}

/**
 * The details of wireless debugging: address, state and shortcuts to the pairing screens of Settings.
 * With [easyAdb] it explains how to reach this phone from EasyADB and turns wireless debugging on,
 * because nothing works without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirelessSheet(model: MainViewModel, easyAdb: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberWirelessState()
    LaunchedEffect(Unit) {
        if (easyAdb && !WirelessInfo.isEnabled(context)) model.run { WirelessDebug(SuRoot).setEnabled(true) }
    }

    fun open(action: (WirelessDebug) -> Unit) = model.run {
        try {
            action(WirelessDebug(SuRoot))
        } catch (e: AppException) {
            if (e.code != ErrorCode.NOT_FOUND) throw e
            // The page differs on some ROMs; Developer options always exist.
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (easyAdb) {
                Text(
                    stringResource(R.string.easyadb_title), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.easyadb_hint), Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingRow(title = stringResource(R.string.wireless_switch)) { WirelessSwitch(model, state) }
            if (state.enabled) {
                val address = state.address
                SettingRow(
                    title = stringResource(R.string.wireless_address),
                    summary = address ?: state.ip?.let { stringResource(R.string.wireless_port_unknown, it) }
                        ?: stringResource(R.string.wireless_no_wifi),
                ) {
                    if (address != null) {
                        TextButton({
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", address))
                        }) { Text(stringResource(R.string.copy)) }
                    }
                }
                SettingRow(title = stringResource(if (state.connected) R.string.wireless_connected else R.string.wireless_not_connected))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton({ open { it.openQrPairing() } }, Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        Text(stringResource(R.string.wireless_qr), textAlign = TextAlign.Center)
                    }
                    FilledTonalButton({ open { it.openPage() } }, Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        Text(stringResource(R.string.wireless_code), textAlign = TextAlign.Center)
                    }
                }
                Text(
                    stringResource(R.string.wireless_on), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
