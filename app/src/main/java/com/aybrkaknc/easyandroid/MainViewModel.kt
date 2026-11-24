package com.aybrkaknc.easyandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aybrkaknc.easyandroid.core.StoredBackups
import com.aybrkaknc.easyandroid.core.SuRoot
import com.aybrkaknc.easyandroid.job.UiText
import com.aybrkaknc.easyandroid.job.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RootState { CHECKING, GRANTED, MISSING }

class MainViewModel : ViewModel() {
    val root = MutableStateFlow(RootState.CHECKING)

    /** Version of the root manager, if it tells. */
    val rootVersion = MutableStateFlow<String?>(null)

    /** Null while loading. */
    val apps = MutableStateFlow<List<AppInfo>?>(null)
    val backups = MutableStateFlow<StoredBackups?>(null)
    val error = MutableStateFlow<UiText?>(null)

    init {
        checkRoot()
    }

    fun checkRoot() {
        root.value = RootState.CHECKING
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) { SuRoot.retry() }
            root.value = if (granted) RootState.GRANTED else RootState.MISSING
            if (granted) {
                refresh()
                rootVersion.value = withContext(Dispatchers.IO) {
                    runCatching { SuRoot.run("su -v").out.lines().first().trim().ifEmpty { null } }.getOrNull()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            apps.value = loadApps(Env.app)
        }
        refreshBackups()
    }

    fun refreshBackups() = run { backups.value = Env.store().list() }

    /** Runs [block] off the main thread and shows its error, if any. */
    fun run(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                error.value = errorText(e)
            }
        }
    }
}
