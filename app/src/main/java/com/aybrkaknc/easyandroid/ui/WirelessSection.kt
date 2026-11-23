package com.aybrkaknc.easyandroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

/** Wireless debugging: switch, address and shortcuts to the pairing screens of Settings. */
@Composable
fun WirelessSection(model: MainViewModel) {
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
    val address = port?.let { p -> ip?.let { "$it:$p" } }

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

    Heading(stringResource(R.string.wireless_title))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.wireless_switch))
            Hint(stringResource(if (enabled) R.string.wireless_on else R.string.wireless_off))
        }
        Switch(enabled, onCheckedChange = { on -> model.run { WirelessDebug(SuRoot).setEnabled(on) } })
    }
    if (enabled) {
        val shown = address
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Hint(stringResource(R.string.wireless_address))
                Text(
                    when {
                        shown != null -> shown
                        ip != null -> stringResource(R.string.wireless_port_unknown, ip.orEmpty())
                        else -> stringResource(R.string.wireless_no_wifi)
                    },
                )
            }
            if (shown != null) {
                TextButton({
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", shown))
                }) { Text(stringResource(R.string.copy)) }
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton({ open { it.openQrPairing() } }, Modifier.fillMaxWidth(), enabled = enabled) {
            Text(stringResource(R.string.wireless_qr))
        }
        OutlinedButton({ open { it.openPage() } }, Modifier.fillMaxWidth(), enabled = enabled) {
            Text(stringResource(R.string.wireless_code))
        }
    }
    Hint(stringResource(if (enabled) R.string.wireless_help else R.string.wireless_turn_on))
}
