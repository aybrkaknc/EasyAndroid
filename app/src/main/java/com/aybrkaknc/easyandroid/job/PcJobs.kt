package com.aybrkaknc.easyandroid.job

import android.content.Context
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.FileSetKind
import com.aybrkaknc.easyandroid.core.PcJob
import com.aybrkaknc.easyandroid.core.PcStatus
import com.aybrkaknc.easyandroid.core.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Backups that the PC asks the phone for: messages, call logs, wallpapers and system settings.
 * EasyADB leaves a job file in the shared folder and starts the app; the work is the same as when
 * the user starts it here, and how it goes is written back for the PC.
 */
object PcJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What a job of `files` may ask for. Folders are streamed by the PC itself. */
    private val KINDS = mapOf(
        "messages" to FileSetKind.MESSAGES,
        "calls" to FileSetKind.CALLS,
        "wallpaper" to FileSetKind.WALLPAPER,
    )

    /** Runs the job that the PC left, if any. Meant to be called off the main thread. */
    fun runPending(context: Context) {
        val link = Env.pc()
        val job = runCatching { link.pendingJob() }.getOrNull() ?: return
        // One job at a time: the PC is told when this phone is busy, and the job file stays for later.
        if (Jobs.state.value?.finished == false) {
            link.reportJob(PcStatus(job.id, "failed", error = context.getString(R.string.pc_job_busy)))
            link.takeJob()
            return
        }
        link.takeJob()
        run(context, job)
    }

    private fun run(context: Context, job: PcJob) {
        val link = Env.pc()
        val items: List<Item> = when (job.type) {
            "files" -> job.kinds.map { name -> KINDS[name]?.let(Item::Files) ?: return refuse(job, name) }
            "system" -> listOf(Item.System)
            else -> return refuse(job, job.type)
        }
        if (items.isEmpty()) return refuse(job, "")
        // The ids of the backups made, which the PC copies when this job is done.
        val made = ArrayList<String>()
        link.reportJob(PcStatus(job.id, "running", stage = "prepare"))
        Jobs.start(
            context, R.string.job_backup, items, { it.label(context) },
            after = {
                val state = Jobs.state.value
                val failed = state?.results.orEmpty().filter { it.kind == ResultKind.FAILED }
                val status = if (state?.cancelling == true) {
                    PcStatus(job.id, "cancelled")
                } else if (failed.isEmpty() && made.size == items.size) {
                    PcStatus(job.id, "done", notes = made.toList())
                } else {
                    val cause = failed.firstOrNull()?.lines?.firstOrNull()?.resolve(context)
                    PcStatus(job.id, "failed", error = cause ?: errorTextFor(ErrorCode.CANCELLED, context))
                }
                runCatching { link.reportJob(status) }
            },
        ) { item, task ->
            when (item) {
                is Item.Files -> made += "id:" + Env.files().backup(item.kind, emptyList(), Env.backups().snapshot(), task)
                Item.System -> {
                    task.progress(Stage.SETTINGS)
                    val system = Env.system()
                    made += "system:" + system.save(system.available(wifi = false), Env.backups().snapshot())
                }
            }
            emptyList()
        }
        // Keeps the PC informed, and lets it cancel.
        scope.launch {
            while (true) {
                val state = Jobs.state.value ?: break
                if (state.finished) break
                runCatching {
                    link.reportJob(
                        PcStatus(job.id, "running", stage = state.stage.name.lowercase(), item = state.item, done = state.done, total = state.size),
                    )
                    if (link.cancelRequested(job.id)) Jobs.cancel()
                }
                delay(1000)
            }
        }
    }

    private fun refuse(job: PcJob, what: String) {
        Env.pc().reportJob(PcStatus(job.id, "failed", error = "unsupported: $what"))
    }

    private fun errorTextFor(code: ErrorCode, context: Context): String =
        errorText(AppException(code)).resolve(context)

    private sealed interface Item {
        fun label(context: Context): String

        class Files(val kind: FileSetKind) : Item {
            override fun label(context: Context) = context.getString(
                when (kind) {
                    FileSetKind.MESSAGES -> R.string.chip_messages
                    FileSetKind.CALLS -> R.string.chip_calls
                    FileSetKind.WALLPAPER -> R.string.chip_wallpapers
                    FileSetKind.FOLDERS -> R.string.chip_folders
                    FileSetKind.MEDIA -> R.string.kind_media
                },
            )
        }

        data object System : Item {
            override fun label(context: Context) = context.getString(R.string.tab_system)
        }
    }
}
