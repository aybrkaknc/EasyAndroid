package com.aybrkaknc.easyandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aybrkaknc.easyandroid.core.PcRecord
import com.aybrkaknc.easyandroid.core.StoredBackups
import com.aybrkaknc.easyandroid.core.SuRoot
import com.aybrkaknc.easyandroid.job.PcJobs
import com.aybrkaknc.easyandroid.job.UiText
import com.aybrkaknc.easyandroid.job.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RootState { CHECKING, GRANTED, MISSING }

class MainViewModel : ViewModel() {
    val root = MutableStateFlow(RootState.CHECKING)

    /** Null while loading. */
    val apps = MutableStateFlow<List<AppInfo>?>(null)
    val backups = MutableStateFlow<StoredBackups?>(null)
    val error = MutableStateFlow<UiText?>(null)

    /** App backups that the PC holds, as EasyADB reports them. */
    val pcBackups = MutableStateFlow<List<PcRecord>>(emptyList())

    /** Backups that the PC has now as well, offered for removal from this phone. */
    val deleteOffer = MutableStateFlow<List<String>>(emptyList())

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
                // The PC may have left a job while the app was closed.
                withContext(Dispatchers.IO) { runCatching { PcJobs.runPending(Env.app) } }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            apps.value = loadApps(Env.app)
        }
        refreshBackups()
    }

    fun refreshBackups() = run {
        backups.value = Env.store().list()
        pcBackups.value = runCatching { Env.pc().records() }.getOrDefault(emptyList())
    }

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
