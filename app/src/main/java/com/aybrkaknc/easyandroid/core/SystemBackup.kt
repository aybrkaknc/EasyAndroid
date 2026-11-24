package com.aybrkaknc.easyandroid.core

import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class WifiNetwork(val ssid: String, val security: String, val password: String? = null)

@Serializable
data class SystemSnapshot(
    val format: String,
    val version: Int,
    val createdAt: String,
    val device: DeviceSnapshot,
    val settings: Map<String, Map<String, String>> = emptyMap(),
    val roles: Map<String, List<String>> = emptyMap(),
    val wifi: List<WifiNetwork> = emptyList(),
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "easyandroid-system"

        fun parse(text: String): SystemSnapshot {
            val snapshot = try {
                JSON.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                throw AppException(ErrorCode.BACKUP_INVALID, e.message.orEmpty())
            }
            if (snapshot.format != FORMAT || snapshot.version != 1) {
                throw AppException(ErrorCode.BACKUP_INVALID, snapshot.format)
            }
            return snapshot
        }
    }
}

sealed interface SystemItem {
    data class Setting(val namespace: String, val key: String, val value: String, val current: String?) : SystemItem
    data class Role(val role: String, val packageName: String, val current: String?) : SystemItem
    data class Wifi(val network: WifiNetwork) : SystemItem
}

/** Settings, default apps and Wi-Fi networks: what a new ROM loses besides apps. */
class SystemBackup(private val shell: Root, private val store: BackupStore) {
    fun create(
        settings: Boolean,
        roles: Boolean,
        wifi: Boolean,
        device: DeviceSnapshot,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): String = save(available(settings, roles, wifi), device, now)

    /** What the phone has now, for the user to choose from. Values of settings are the current ones. */
    fun available(settings: Boolean = true, roles: Boolean = true, wifi: Boolean = true): List<SystemItem> {
        val items = ArrayList<SystemItem>()
        if (settings) {
            for (namespace in NAMESPACES) {
                currentSettings(namespace).filterKeys(::isRestorable)
                    .forEach { (key, value) -> items += SystemItem.Setting(namespace, key, value, null) }
            }
        }
        if (roles) {
            for (role in ROLES) holders(role).forEach { items += SystemItem.Role(role, it, null) }
        }
        if (wifi) savedNetworks().forEach { items += SystemItem.Wifi(it) }
        return items
    }

    /** Writes the chosen [items] as a system backup file and returns its name. */
    fun save(items: List<SystemItem>, device: DeviceSnapshot, now: OffsetDateTime = OffsetDateTime.now()): String {
        store.ensureRoot()
        val settings = items.filterIsInstance<SystemItem.Setting>()
            .filter { it.namespace in NAMESPACES && isRestorable(it.key) }
            .groupBy({ it.namespace }, { it.key to it.value }).mapValues { it.value.toMap() }
        val roles = items.filterIsInstance<SystemItem.Role>()
            .filter { it.role in ROLES && Sh.isPackage(it.packageName) }
            .groupBy({ it.role }, { it.packageName })
        val snapshot = SystemSnapshot(
            format = SystemSnapshot.FORMAT,
            version = 1,
            createdAt = timestamp(now),
            device = device,
            settings = settings,
            roles = roles,
            wifi = items.filterIsInstance<SystemItem.Wifi>().map { it.network },
        )
        val name = folderName("system", now) + ".json"
        store.writeText(store.dir(name), snapshot.encode())
        return name
    }

    private fun currentSettings(namespace: String) =
        Parsers.settings(shell.run("settings list --user 0 $namespace").orThrow())

    private fun holders(role: String): List<String> =
        shell.run("cmd role get-role-holders --user 0 $role").out
            .split(Regex("[;,\\s]+")).filter { Sh.isPackage(it) }

    private fun savedNetworks(): List<WifiNetwork> {
        for (path in WIFI_STORES) {
            val result = shell.run("cat $path")
            if (result.ok && result.out.isNotBlank()) return Parsers.wifi(result.out)
        }
        throw AppException(ErrorCode.UNSUPPORTED, "WifiConfigStore.xml")
    }

    /** What applying [snapshot] would change. */
    fun diff(snapshot: SystemSnapshot): List<SystemItem> {
        val items = ArrayList<SystemItem>()
        for ((namespace, values) in snapshot.settings) {
            if (namespace !in NAMESPACES) continue
            val current = currentSettings(namespace)
            values.filter { (key, value) -> isRestorable(key) && current[key] != value }
                .forEach { (key, value) -> items += SystemItem.Setting(namespace, key, value, current[key]) }
        }
        for ((role, packages) in snapshot.roles) {
            if (role !in ROLES) continue
            val current = holders(role)
            packages.filter { Sh.isPackage(it) && it !in current }
                .forEach { items += SystemItem.Role(role, it, current.firstOrNull()) }
        }
        val known = if (snapshot.wifi.isEmpty()) emptySet() else
            runCatching { savedNetworks().map { it.ssid to it.security }.toSet() }.getOrDefault(emptySet())
        snapshot.wifi.filter { (it.ssid to it.security) !in known }.forEach { items += SystemItem.Wifi(it) }
        return items
    }

    fun apply(item: SystemItem) {
        when (item) {
            is SystemItem.Setting -> {
                if (item.namespace !in NAMESPACES || !isRestorable(item.key)) {
                    throw AppException(ErrorCode.PROTECTED, item.key)
                }
                shell.run("settings put --user 0 ${item.namespace} " + Sh.join(item.key, item.value)).orThrow()
            }
            is SystemItem.Role -> {
                if (item.role !in ROLES) throw AppException(ErrorCode.INVALID_INPUT, item.role)
                Sh.checkPackage(item.packageName)
                shell.run("cmd role add-role-holder --user 0 ${item.role} ${item.packageName}").orThrow()
            }
            is SystemItem.Wifi -> addNetwork(item.network)
        }
    }

    private fun addNetwork(network: WifiNetwork) {
        if (network.security !in setOf("open", "owe", "wpa2", "wpa3")) {
            throw AppException(ErrorCode.INVALID_INPUT, network.security)
        }
        val args = listOfNotNull(network.ssid, network.security, network.password).toTypedArray()
        val result = shell.run("cmd wifi add-network " + Sh.join(*args))
        val text = (result.out + "\n" + result.err).trim()
        // Older versions do not know the command and print their help instead.
        if (!result.ok || "Unknown command" in text || "failed" in text.lowercase()) {
            throw AppException(ErrorCode.UNSUPPORTED, text.lines().first())
        }
    }

    companion object {
        val NAMESPACES = listOf("system", "secure", "global")

        val ROLES = listOf("BROWSER", "DIALER", "SMS", "HOME", "ASSISTANT", "CALL_SCREENING", "CALL_REDIRECTION")
            .map { "android.app.role.$it" }

        private val WIFI_STORES = listOf(
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/wifi/WifiConfigStore.xml",
        )

        /** Identity, provisioning, security and state of one installation; never copied to another. */
        private val BLOCKED = listOf(
            "android_id", "bluetooth_", "provisioned", "setup_complete", "setup_", "adb_", "development_settings",
            "boot_count", "advertising_id", "token", "password", "lock_pattern", "lockscreen.", "lock_screen_",
            "package_verifier", "verifier_", "install_non_market", "http_proxy", "cert", "gservices", "last_", "_version",
            "timestamp", "_time_", "sim_", "subscription", "multi_sim", "preferred_network_mode", "mobile_data",
            "device_policy", "user_switcher", "trust_agent", "enabled_accessibility_services", "accessibility_enabled",
            "enabled_notification_listeners", "mock_location", "credential_service", "autofill_service_search",
            "secure_frp", "backup_", "wifi_on", "airplane_mode_on", "zen_", "database_", "config_", "factory_reset",
        )

        fun isRestorable(key: String): Boolean = Sh.isToken(key) && BLOCKED.none { it in key.lowercase() }

        /** Plain Android preferences that mean the same on every ROM, selected by default. */
        private val COMMON = setOf(
            "screen_brightness_mode", "screen_off_timeout", "font_scale", "accelerometer_rotation",
            "haptic_feedback_enabled", "sound_effects_enabled", "dtmf_tone", "lockscreen_sounds_enabled",
            "vibrate_when_ringing", "time_12_24", "status_bar_show_battery_percent", "pointer_speed",
            "notification_light_pulse", "ui_night_mode", "doze_enabled", "doze_always_on", "wake_gesture_enabled",
            "double_tap_to_wake", "camera_double_tap_power_gesture_disabled", "long_press_timeout",
            "show_ime_with_hard_keyboard", "spell_checker_enabled", "night_display_auto_mode",
            "night_display_color_temperature", "sysui_qs_tiles", "icon_blacklist", "window_animation_scale",
            "transition_animation_scale", "animator_duration_scale", "low_power_trigger_level",
            "heads_up_notifications_enabled", "private_dns_mode", "private_dns_specifier", "device_name",
            "charging_sounds_enabled", "charging_vibration_enabled", "lock_to_app_enabled",
        )

        fun isCommon(item: SystemItem): Boolean = item !is SystemItem.Setting || item.key in COMMON
    }
}
