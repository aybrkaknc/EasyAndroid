package com.aybrkaknc.easyandroid.core

/**
 * Wireless debugging (Android 11+). The pairing code and QR code are made by Settings, so this
 * only switches the feature and opens Settings screens that are not exported.
 */
class WirelessDebug(private val shell: Root) {
    fun setEnabled(enabled: Boolean) {
        shell.run("settings put global $SETTING ${if (enabled) 1 else 0}").orThrow()
    }

    /**
     * Port that adbd listens on for Wireless debugging. mDNS would need the INTERNET permission,
     * so the listening sockets are read instead. Null while the feature is starting or off.
     */
    fun listeningPort(): Int? {
        val result = shell.run("ss -ltnp 2>/dev/null; netstat -ltnp 2>/dev/null")
        return if (result.ok || result.out.isNotBlank()) parsePort(result.out) else null
    }

    /** The screen that scans a QR code, shown by EasyADB, on the phone. */
    fun openQrPairing() = start("-n $QR_ACTIVITY")

    /** The Wireless debugging page with "Pair device with pairing code". */
    fun openPage() = start("-n $SUB_SETTINGS --es :settings:show_fragment $PAGE_FRAGMENT")

    private fun start(args: String) {
        val result = shell.run("am start --user 0 $args")
        val text = (result.out + "\n" + result.err).trim()
        // `am` exits with 0 even when the activity does not exist.
        if (!result.ok || "Error" in text || "Exception" in text) throw AppException(ErrorCode.NOT_FOUND, text.lines().first())
    }

    companion object {
        private val SS = Regex("""LISTEN\s+\d+\s+\d+\s+\S*:(\d+)\s+\S+\s+users:\(\("adbd"""")
        private val NETSTAT = Regex("""\S*:(\d+)\s+\S+\s+LISTEN\s+\d+/adbd""")

        /** Reads `ss -ltnp` or `netstat -ltnp` output. The old TCP mode port 5555 is used last. */
        fun parsePort(output: String): Int? {
            val ports = output.lineSequence().mapNotNull { line ->
                (SS.find(line) ?: NETSTAT.find(line))?.groupValues?.get(1)?.toIntOrNull()
            }.toList()
            return ports.firstOrNull { it != 5555 } ?: ports.firstOrNull()
        }

        const val SETTING = "adb_wifi_enabled"
        const val QR_ACTIVITY = "com.android.settings/.development.AdbQrCodeActivity"
        const val SUB_SETTINGS = "com.android.settings/.SubSettings"
        const val PAGE_FRAGMENT = "com.android.settings.development.AdbWirelessDebuggingFragment"
    }
}
