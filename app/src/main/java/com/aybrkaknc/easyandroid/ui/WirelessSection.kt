package com.aybrkaknc.easyandroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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

/** Wireless debugging: switch, address, state and shortcuts to the pairing screens of Settings. */
@Composable
fun WirelessControls(model: MainViewModel) {
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
    val connected by produceState(false, port) {
        val p = port ?: return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { WirelessDebug(SuRoot).isConnected(p) }.getOrDefault(false) }
            delay(4000)
        }
    }
    val shown = port?.let { p -> ip?.let { "$it:$p" } }

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

    SettingRow(
        title = stringResource(R.string.wireless_switch),
        summary = stringResource(if (enabled) R.string.wireless_on else R.string.wireless_off),
        onClick = { model.run { WirelessDebug(SuRoot).setEnabled(!enabled) } },
    ) { Switch(enabled, onCheckedChange = null) }
    if (enabled) {
        SettingRow(
            title = stringResource(R.string.wireless_address),
            summary = when {
                shown != null -> shown
                ip != null -> stringResource(R.string.wireless_port_unknown, ip.orEmpty())
                else -> stringResource(R.string.wireless_no_wifi)
            },
        ) {
            if (shown != null) {
                TextButton({
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", shown))
                }) { Text(stringResource(R.string.copy)) }
            }
        }
        SettingRow(title = stringResource(if (connected) R.string.wireless_connected else R.string.wireless_not_connected))
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
    }
}

/** How to reach this phone from EasyADB. Turns Wireless debugging on, because nothing works without it. */
@Composable
fun EasyAdbDialog(model: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!WirelessInfo.isEnabled(context)) model.run { WirelessDebug(SuRoot).setEnabled(true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.easyadb_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.easyadb_hint))
                WirelessControls(model)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
