package com.aybrkaknc.easyandroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.aybrkaknc.easyandroid.core.WirelessDebug
import java.net.Inet4Address

/** What the app can read about Wireless debugging without root. */
object WirelessInfo {
    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, WirelessDebug.SETTING, 0) == 1

    /** IPv4 address of this phone on Wi-Fi, if it is connected. */
    fun wifiAddress(context: Context): String? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        for (network in manager.allNetworks) {
            val capabilities = manager.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val address = manager.getLinkProperties(network)?.linkAddresses
                ?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull()
            if (address != null) return address.hostAddress
        }
        return null
    }
}
