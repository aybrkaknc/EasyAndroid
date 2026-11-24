package com.aybrkaknc.easyandroid

import android.content.Context
import androidx.core.content.edit
import com.aybrkaknc.easyandroid.core.BackupOptions
import com.aybrkaknc.easyandroid.core.DEFAULT_ROOT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

data class Settings(val backupRoot: String, val theme: ThemeMode, val pinned: Set<String>, val backup: BackupOptions)

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(load())
    val state: StateFlow<Settings> = flow

    private fun load() = Settings(
        backupRoot = prefs.getString("backupRoot", null) ?: DEFAULT_ROOT,
        theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.SYSTEM,
        pinned = prefs.getStringSet("pinned", null)?.toSet() ?: setOf("backup_all", "restore_all"),
        backup = BackupOptions(
            obb = prefs.getBoolean("backupObb", true),
            data = prefs.getBoolean("backupData", true),
            externalData = prefs.getBoolean("backupExternal", false),
            compress = prefs.getBoolean("backupCompress", false),
        ),
    )

    fun setBackupRoot(path: String?) {
        prefs.edit { putString("backupRoot", path) }
        flow.value = load()
    }

    /** Quick actions shown on Home. */
    fun togglePinned(id: String) {
        val pinned = flow.value.pinned
        prefs.edit { putStringSet("pinned", if (id in pinned) pinned - id else pinned + id) }
        flow.value = load()
    }

    /** What the backup dialog starts with. */
    fun setBackupOptions(options: BackupOptions) {
        prefs.edit {
            putBoolean("backupObb", options.obb)
            putBoolean("backupData", options.data)
            putBoolean("backupExternal", options.externalData)
            putBoolean("backupCompress", options.compress)
        }
        flow.value = load()
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString("theme", mode.name) }
        flow.value = load()
    }
}
