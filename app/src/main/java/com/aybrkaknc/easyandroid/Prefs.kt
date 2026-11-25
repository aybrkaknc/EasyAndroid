package com.aybrkaknc.easyandroid

import android.content.Context
import androidx.core.content.edit
import com.aybrkaknc.easyandroid.core.BackupOptions
import com.aybrkaknc.easyandroid.core.DEFAULT_ROOT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

/** A PC whose jobs start on this phone without asking. */
data class TrustedPc(val id: String, val name: String)

data class Settings(val backupRoot: String, val theme: ThemeMode, val backup: BackupOptions, val trustedPcs: List<TrustedPc>)

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(load())
    val state: StateFlow<Settings> = flow

    private fun load() = Settings(
        backupRoot = prefs.getString("backupRoot", null) ?: DEFAULT_ROOT,
        theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.SYSTEM,
        backup = BackupOptions(
            obb = prefs.getBoolean("backupObb", true),
            data = prefs.getBoolean("backupData", true),
            externalData = prefs.getBoolean("backupExternal", false),
            compress = prefs.getBoolean("backupCompress", false),
        ),
        trustedPcs = prefs.getStringSet("trustedPcs", null).orEmpty()
            .map { TrustedPc(it.substringBefore(' '), it.substringAfter(' ')) }.sortedBy { it.name },
    )

    fun trustedPcs(): List<TrustedPc> = flow.value.trustedPcs

    fun trustPc(pc: TrustedPc) = setTrusted(trustedPcs().filter { it.id != pc.id } + pc)

    fun forgetPc(id: String) = setTrusted(trustedPcs().filter { it.id != id })

    private fun setTrusted(list: List<TrustedPc>) {
        prefs.edit { putStringSet("trustedPcs", list.map { "${it.id} ${it.name}" }.toSet()) }
        flow.value = load()
    }

    fun setBackupRoot(path: String?) {
        prefs.edit { putString("backupRoot", path) }
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
