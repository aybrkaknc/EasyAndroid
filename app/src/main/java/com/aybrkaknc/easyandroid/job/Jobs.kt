package com.aybrkaknc.easyandroid.job

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import android.content.Intent
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.Note
import com.aybrkaknc.easyandroid.core.NoteDetail
import com.aybrkaknc.easyandroid.core.Stage
import com.aybrkaknc.easyandroid.core.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Text that is translated when it is shown. */
data class UiText(@param:StringRes val id: Int, val args: List<String> = emptyList()) {
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

enum class ResultKind { OK, WARNING, FAILED }

data class JobResult(val name: String, val kind: ResultKind, val lines: List<UiText>)

data class JobState(
    @param:StringRes val title: Int,
    val total: Int,
    val index: Int = 0,
    val item: String = "",
    val stage: Stage = Stage.PREPARE,
    val done: Long = 0,
    val size: Long = 0,
    val cancelling: Boolean = false,
    val finished: Boolean = false,
    val results: List<JobResult> = emptyList(),
)

/** Runs one job at a time. A failing item never stops the rest; everything is reported at the end. */
object Jobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flow = MutableStateFlow<JobState?>(null)
    val state: StateFlow<JobState?> = flow

    @Volatile
    private var cancelled = false

    fun <T> start(
        context: Context,
        @StringRes title: Int,
        items: List<T>,
        name: (T) -> String,
        after: () -> Unit = {},
        work: (T, Task) -> List<UiText>,
    ) {
        if (items.isEmpty() || flow.value?.finished == false) return
        val app = context.applicationContext
        cancelled = false
        flow.value = JobState(title, items.size)
        runCatching { ContextCompat.startForegroundService(app, Intent(app, JobService::class.java)) }
        scope.launch {
            val lock = app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyAndroid:job")
            lock.acquire(6 * 60 * 60 * 1000L)
            try {
                for ((index, item) in items.withIndex()) {
                    if (cancelled) break
                    val label = name(item)
                    flow.update { it?.copy(index = index, item = label, stage = Stage.PREPARE, done = 0, size = 0) }
                    val result = try {
                        val lines = work(item, task())
                        JobResult(label, if (lines.any { it.id in WARNINGS }) ResultKind.WARNING else ResultKind.OK, lines)
                    } catch (e: Exception) {
                        if (e is AppException && e.code == ErrorCode.CANCELLED) break
                        JobResult(label, ResultKind.FAILED, listOf(errorText(e)))
                    }
                    flow.update { it?.copy(results = it.results + result) }
                }
            } finally {
                if (lock.isHeld) lock.release()
                flow.update { it?.copy(finished = true, index = it.results.size) }
                flow.value?.let { ReportLog.add(app, it) }
                after()
            }
        }
    }

    private fun task(): Task {
        var last = 0L
        return Task({ cancelled }) { stage, done, size ->
            val now = SystemClock.elapsedRealtime()
            if (now - last > 150 || flow.value?.stage != stage) {
                last = now
                flow.update { it?.copy(stage = stage, done = done, size = size) }
            }
        }
    }

    fun cancel() {
        cancelled = true
        flow.update { it?.copy(cancelling = true) }
    }

    fun dismiss() {
        if (flow.value?.finished == true) flow.value = null
    }

    private val WARNINGS = Note.entries.map { noteId(it) }.toSet()
}

private fun noteId(note: Note): Int = when (note) {
    Note.DATA_FAILED -> R.string.note_data_failed
    Note.DEVICE_DATA_FAILED -> R.string.note_device_data_failed
    Note.EXTERNAL_DATA_FAILED -> R.string.note_external_data_failed
    Note.OBB_FAILED -> R.string.note_obb_failed
    Note.PERMISSIONS_PARTIAL -> R.string.note_permissions_partial
    Note.APP_OPS_FAILED -> R.string.note_app_ops_failed
}

fun noteText(note: NoteDetail) = UiText(noteId(note.note), listOf(note.detail.trim().take(300)))

fun errorText(error: Throwable): UiText {
    val detail = ((error as? AppException)?.detail ?: error.message ?: error.javaClass.simpleName).trim().take(300)
    val id = when ((error as? AppException)?.code) {
        ErrorCode.NO_ROOT -> R.string.err_no_root
        ErrorCode.INVALID_INPUT -> R.string.err_invalid_input
        ErrorCode.NOT_FOUND -> R.string.err_not_found
        ErrorCode.NO_SPACE -> R.string.err_no_space
        ErrorCode.BACKUP_INVALID -> R.string.err_backup_invalid
        ErrorCode.INTEGRITY -> R.string.err_integrity
        ErrorCode.INSTALL_FAILED -> R.string.err_install_failed
        ErrorCode.PROTECTED -> R.string.err_protected
        ErrorCode.CANCELLED -> R.string.err_cancelled
        ErrorCode.UNSUPPORTED -> R.string.err_unsupported
        ErrorCode.PC_MISSING -> R.string.err_pc_missing
        ErrorCode.PC_LOST -> R.string.err_pc_lost
        ErrorCode.PC_REJECTED -> R.string.err_pc_rejected
        ErrorCode.PC_FAILED -> R.string.err_pc_failed
        ErrorCode.COMMAND_FAILED, null -> R.string.err_command_failed
    }
    return UiText(id, listOf(detail))
}

fun stageId(stage: Stage): Int = when (stage) {
    Stage.PREPARE -> R.string.stage_prepare
    Stage.APK -> R.string.stage_apk
    Stage.OBB -> R.string.stage_obb
    Stage.DATA -> R.string.stage_data
    Stage.FILES -> R.string.stage_files
    Stage.VERIFY -> R.string.stage_verify
    Stage.INSTALL -> R.string.stage_install
    Stage.PERMISSIONS -> R.string.stage_permissions
    Stage.DEBLOAT -> R.string.stage_debloat
    Stage.SETTINGS -> R.string.stage_settings
    Stage.TRANSFER -> R.string.stage_transfer
}

/** What the PC reports back about a backup it took, as lines of the job report. */
fun pcNoteText(note: String): UiText {
    val kind = note.substringBefore(':')
    val detail = note.substringAfter(':', "")
    return if (kind == "data_failed") UiText(R.string.note_data_failed, listOf(detail.trim().take(300)))
    else UiText(R.string.note_pc_data_skipped)
}
