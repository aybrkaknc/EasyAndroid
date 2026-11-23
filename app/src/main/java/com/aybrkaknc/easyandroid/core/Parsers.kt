package com.aybrkaknc.easyandroid.core

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

object Parsers {
    fun pmPath(out: String): List<String> =
        out.lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }

    fun versionName(dump: String): String? =
        dump.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("versionName=") }
            ?.removePrefix("versionName=")?.trim()

    /** Parses the `runtime permissions:` blocks of `dumpsys package <pkg>`; depends on indentation. */
    fun runtimePermissions(dump: String): List<PermissionGrant> {
        val found = sortedMapOf<String, Boolean>()
        var header = -1
        for (line in dump.lineSequence()) {
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            if (header >= 0 && indent > header) {
                val name = line.trim().substringBefore(':', "").trim()
                if (name.isNotEmpty() && ' ' !in name) found[name] = line.contains("granted=true")
            } else {
                header = if (line.trim() == "runtime permissions:") indent else -1
            }
        }
        return found.map { PermissionGrant(it.key, it.value) }
    }

    /**
     * App ops a user sets by hand ("special app access"). All others follow the runtime
     * permissions or are managed by the system.
     */
    private val USER_OPS = setOf(
        "SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "REQUEST_INSTALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE",
        "GET_USAGE_STATS", "PICTURE_IN_PICTURE", "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND",
        "SCHEDULE_EXACT_ALARM", "MANAGE_MEDIA", "USE_FULL_SCREEN_INTENT", "AUTO_REVOKE_PERMISSIONS_IF_UNUSED",
    )
    private val MODES = setOf("allow", "ignore", "deny", "default", "foreground")

    fun isUserOp(op: AppOp) = op.op in USER_OPS && op.mode in MODES

    fun appOps(out: String): List<AppOp> {
        val found = linkedMapOf<String, String>()
        for (line in out.lineSequence()) {
            val parts = line.trim().removePrefix("Uid mode: ").split(": ", limit = 2)
            if (parts.size != 2) continue
            val op = AppOp(parts[0], parts[1].substringBefore(';').trim())
            // The uid mode is printed first and wins over the package mode.
            if (isUserOp(op) && op.mode != "default") found.putIfAbsent(op.op, op.mode)
        }
        return found.map { AppOp(it.key, it.value) }
    }

    /** Packages the user exempted from battery optimization. */
    fun batteryExempt(out: String): Set<String> =
        out.lineSequence().map { it.trim().split(',') }
            .filter { it.size >= 2 && it[0] == "user" }.map { it[1] }.toSet()

    private val SETTING = Regex("([A-Za-z0-9_.:\\-/]+)=(.*)")

    /** `settings list` output. Values that span several lines cannot be told apart safely and are dropped. */
    fun settings(out: String): Map<String, String> {
        val found = sortedMapOf<String, String>()
        var last: String? = null
        for (line in out.lineSequence()) {
            val match = SETTING.matchEntire(line)
            if (match == null) {
                if (line.isNotEmpty()) last?.let { found.remove(it) }
                continue
            }
            last = match.groupValues[1]
            if (match.groupValues[2] != "null") found[last] = match.groupValues[2]
        }
        return found
    }

    /** Reads saved networks from WifiConfigStore.xml. Enterprise and WEP networks are left out. */
    fun wifi(xml: String): List<WifiNetwork> {
        val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val configs = document.getElementsByTagName("WifiConfiguration")
        return (0 until configs.length).mapNotNull { index ->
            val config = configs.item(index) as Element
            val strings = config.children("string").associate { it.getAttribute("name") to it.textContent }
            val ssid = strings["SSID"]?.unquote() ?: return@mapNotNull null
            val key = strings["ConfigKey"].orEmpty().substringAfterLast('"')
            val password = strings["PreSharedKey"]?.unquote()
            val security = when {
                "EAP" in key || "IEEE8021X" in key || "SUITE_B" in key || "WAPI" in key -> return@mapNotNull null
                "SAE" in key -> "wpa3"
                "PSK" in key -> "wpa2"
                "OWE" in key -> "owe"
                "WEP" in key -> return@mapNotNull null
                else -> "open"
            }
            if (security.startsWith("wpa") && password == null) return@mapNotNull null
            WifiNetwork(ssid, security, password.takeIf { security.startsWith("wpa") })
        }.distinctBy { it.ssid to it.security }
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    /** Text is stored in quotes; anything else is raw hex, which `cmd wifi add-network` cannot take. */
    private fun String.unquote(): String? =
        if (length >= 2 && startsWith('"') && endsWith('"')) substring(1, length - 1) else null
}
