package com.aybrkaknc.easyandroid

import android.content.Context
import androidx.core.content.edit
import com.aybrkaknc.easyandroid.core.DEFAULT_ROOT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(val backupRoot: String)

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(load())
    val state: StateFlow<Settings> = flow

    private fun load() = Settings(backupRoot = prefs.getString("backupRoot", null) ?: DEFAULT_ROOT)

    fun setBackupRoot(path: String?) {
        prefs.edit { putString("backupRoot", path) }
        flow.value = load()
    }
}
