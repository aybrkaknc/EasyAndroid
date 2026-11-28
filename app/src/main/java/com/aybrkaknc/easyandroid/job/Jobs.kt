package com.aybrkaknc.easyandroid.job

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
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

data class JobResult(
    val name: String,
    val kind: ResultKind,
    val lines: List<UiText>,
    val notTried: Boolean = false,
)

data class JobState(
    @param:StringRes val title: Int,
    val total: Int,
    val index: Int = 0,
    val item: String = "",
    val stage: Stage = Stage.PREPARE,
    val done: Long = 0,
    val size: Long = 0,
    val cancelling: Boolean = false,
    /** The job ended because it was cancelled, here or on the PC. */
    val cancelled: Boolean = false,
    val finished: Boolean = false,
    val results: List<JobResult> = emptyList(),
    /** About the item that runs; after the end, about the first one. */
    val info: ItemInfo = ItemInfo(),
    /** The parts of the current item that moved something; null when that is not known. */
    val seen: Set<Stage>? = null,
    val lane: Lane? = null,
    /** On the clock that counts since the phone started, in milliseconds. */
    val startedAt: Long = 0,
    /** How long the job took, once it has ended. */
    val seconds: Long = 0,
    /** True when what did not make it can be started again. */
    val retryable: Boolean = false,
    /** The window of an incoming transfer was put away. */
    val hidden: Boolean = false,
    /** Cancelling an incoming transfer is being asked about. */
    val askingCancel: Boolean = false,
)

/** Runs one job at a time. A failing item never stops the rest; everything is reported at the end. */
object Jobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flow = MutableStateFlow<JobState?>(null)
    val state: StateFlow<JobState?> = flow

    @Volatile
    private var cancelled = false

    @Volatile
    private var again: (() -> Unit)? = null

    /** [retry] starts the job again for the items it is given, which are those that did not make it. */
    fun <T> start(
        context: Context,
        @StringRes title: Int,
        items: List<T>,
        name: (T) -> String,
        after: () -> Unit = {},
        info: (T) -> ItemInfo = { ItemInfo() },
        retry: ((List<T>) -> Unit)? = null,
        work: (T, Task) -> List<UiText>,
    ) {
        val app = context.applicationContext
        val running = flow.value?.takeIf { !it.finished }
        // The window of an incoming transfer can be put away, so a job can be asked for meanwhile.
        if (running != null && jobKind(running.title) == JobKind.RECEIVE) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, R.string.receive_busy, Toast.LENGTH_SHORT).show() }
        }
        if (items.isEmpty() || running != null) return
        cancelled = false
        again = null
        val startedAt = SystemClock.elapsedRealtime()
        flow.value = JobState(title, items.size, info = info(items.first()), startedAt = startedAt)
        runCatching { ContextCompat.startForegroundService(app, Intent(app, JobService::class.java)) }
        scope.launch {
            val lock = app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyAndroid:job")
            lock.acquire(6 * 60 * 60 * 1000L)
            try {
                fun notTried(from: Int) = items.drop(from).map {
                    JobResult(name(it), ResultKind.FAILED, listOf(UiText(R.string.not_tried)), notTried = true)
                }
                for ((index, item) in items.withIndex()) {
                    if (cancelled) {
                        flow.update { it?.copy(cancelled = true, results = it.results + notTried(index)) }
                        break
                    }
                    val label = name(item)
                    var stop = false
                    val about = info(item)
                    flow.update {
                        it?.copy(
                            index = index, item = label, stage = Stage.PREPARE, done = 0, size = 0,
                            info = about, seen = if (about.remote) null else emptySet(), lane = it.lane?.restart(),
                        )
                    }
                    val result = try {
                        val lines = work(item, task())
                        JobResult(label, if (lines.any { it.id in WARNINGS }) ResultKind.WARNING else ResultKind.OK, lines)
                    } catch (e: Exception) {
                        if (e is AppException && e.code == ErrorCode.CANCELLED) {
                            val here = JobResult(label, ResultKind.FAILED, listOf(UiText(R.string.result_cancelled)))
                            flow.update { it?.copy(cancelled = true, results = it.results + here + notTried(index + 1)) }
                            break
                        }
                        stop = e is AppException && e.code in STOPS
                        JobResult(label, ResultKind.FAILED, listOf(errorText(e)))
                    }
                    flow.update { it?.copy(results = it.results + result) }
                    if (stop) {
                        flow.update { it?.copy(results = it.results + notTried(index + 1)) }
                        break
                    }
                }
            } finally {
                if (lock.isHeld) lock.release()
                // The results are in the order of the items, so a place in one is a place in the other.
                val left = flow.value?.results.orEmpty().withIndex()
                    .filter { it.value.kind == ResultKind.FAILED }.map { items[it.index] }
                if (retry != null && left.isNotEmpty()) again = { retry(left) }
                val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
                flow.update {
                    it?.copy(
                        finished = true, index = it.results.size, info = info(items.first()),
                        seconds = seconds, retryable = again != null,
                    )
                }
                flow.value?.let { ReportLog.add(app, it) }
                after()
            }
        }
    }

    private fun task(): Task {
        var last = 0L
        return Task(
            isCancelled = { cancelled },
            onProgress = { stage, done, size ->
                val now = SystemClock.elapsedRealtime()
                if (now - last > 150 || flow.value?.stage != stage) {
                    last = now
                    flow.update {
                        it?.copy(
                            stage = stage, done = done, size = size,
                            // What the PC does is only known from what it reports.
                            seen = if (it.info.remote) it.seen else it.seen.orEmpty() + stage,
                            lane = if (size > 0) nextLane(it.lane, done, size, now) else it.lane,
                        )
                    }
                }
            },
            onSeen = { stages -> flow.update { it?.copy(seen = stages) } },
        )
    }

    fun retry() {
        val start = again ?: return
        if (flow.value?.finished != true) return
        again = null
        start()
    }

    fun cancel() {
        cancelled = true
        flow.update { it?.copy(cancelling = true, askingCancel = false) }
    }

    fun hide() {
        flow.update { it?.copy(hidden = true, askingCancel = false) }
    }

    fun show() {
        flow.update { it?.copy(hidden = false) }
    }

    fun askCancel(ask: Boolean) {
        flow.update { it?.copy(askingCancel = ask && !it.finished) }
    }

    fun dismiss() {
        if (flow.value?.finished == true) flow.value = null
    }

    /** After these nothing that follows can work either: the PC said no, did not answer or is gone. */
    private val STOPS = setOf(
        ErrorCode.PC_MISSING, ErrorCode.PC_LOST, ErrorCode.PC_REJECTED, ErrorCode.PC_EXPIRED, ErrorCode.PC_BUSY,
        ErrorCode.PC_STOPPED,
    )

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
        ErrorCode.PC_EXPIRED -> R.string.err_pc_expired
        ErrorCode.PC_BUSY -> R.string.err_pc_busy
        ErrorCode.PC_STOPPED -> R.string.err_pc_stopped
        ErrorCode.COMMAND_FAILED, null -> R.string.err_command_failed
    }
    return UiText(id, listOf(detail))
}

fun stageId(stage: Stage): Int = when (stage) {
    Stage.PREPARE -> R.string.stage_prepare
    Stage.APK -> R.string.stage_apk
    Stage.OBB -> R.string.stage_obb
    Stage.DATA -> R.string.stage_data
    Stage.EXTERNAL -> R.string.stage_external
    Stage.FILES -> R.string.stage_files
    Stage.VERIFY -> R.string.stage_verify
    Stage.INSTALL -> R.string.stage_install
    Stage.PERMISSIONS -> R.string.stage_permissions
    Stage.DEBLOAT -> R.string.stage_debloat
    Stage.SETTINGS -> R.string.stage_settings
    Stage.TRANSFER -> R.string.stage_transfer
    Stage.WAITING -> R.string.stage_waiting
    Stage.QUEUED -> R.string.stage_queued
}

/** What the PC reports back about a backup it took, as lines of the job report. */
fun pcNoteText(note: String): UiText {
    val kind = note.substringBefore(':')
    val detail = note.substringAfter(':', "")
    return if (kind == "data_failed") UiText(R.string.note_data_failed, listOf(detail.trim().take(300)))
    else UiText(R.string.note_pc_data_skipped)
}
