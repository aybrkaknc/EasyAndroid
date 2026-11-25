package com.aybrkaknc.easyandroid.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** What the phone asks the PC to do. See "Posta kutusu" in EasyADB's `EASYANDROID_INTEROP.md`. */
@Serializable
data class PcRequest(
    val id: String,
    val created: Long,
    val type: String,
    val phone: String = "",
    @SerialName("package") val packageName: String = "",
    val data: Boolean = false,
    val obb: Boolean = false,
    val externalData: Boolean = false,
    val names: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
    val system: List<String> = emptyList(),
    val format: String = PcLink.REQUEST_FORMAT,
    val version: Int = 1,
)

/** How far the PC got, as it reports it. */
@Serializable
data class PcStatus(
    val id: String = "",
    val state: String = "",
    val stage: String = "",
    val item: String = "",
    val done: Long = 0,
    val total: Long = 0,
    val error: String? = null,
    val notes: List<String> = emptyList(),
) {
    val finished get() = state == "done" || state == "failed" || state == "rejected" || state == "cancelled"
}

/** An app backup that the PC holds. */
@Serializable
data class PcRecord(
    @SerialName("package") val packageName: String,
    val versionName: String? = null,
    val createdAt: String = "",
)

/** A job that the PC asks the phone to do. */
@Serializable
data class PcJob(
    val id: String,
    val created: Long,
    val type: String,
    /** `messages`, `calls`, `wallpaper` for `files`. */
    val kinds: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val format: String = PcLink.JOB_FORMAT,
    val version: Int = 1,
)

/**
 * The phone's side of the folder it shares with EasyADB. EasyAndroid has no network permission,
 * so a job for the PC is a file here that EasyADB finds with adb, and its progress is another.
 */
class PcLink(
    private val shell: Root,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    private fun q(text: String) = Sh.quote(text)

    /** Writes [text] to [name] so that the shell user, which is what adb is, can read it. */
    private fun write(name: String, text: String) {
        val path = "$BOX/$name"
        shell.run(
            "mkdir -p ${q(BOX)} && chown shell:shell ${q(BOX)} && chmod 0770 ${q(BOX)} && " +
                "printf %s ${q(text)} > ${q("$path.tmp")} && chown shell:shell ${q("$path.tmp")} && " +
                "chmod 0660 ${q("$path.tmp")} && mv ${q("$path.tmp")} ${q(path)}",
        ).orThrow()
    }

    private fun read(name: String): String = shell.run("cat ${q("$BOX/$name")} 2>/dev/null").out.trim()

    /** Seconds on the phone's clock. */
    private fun clock(): Long = shell.run("date +%s").out.trim().toLongOrNull() ?: 0

    /** Seconds since EasyADB last looked at the phone, or null when it never did. */
    fun silence(): Long? {
        val out = shell.run("cat ${q("$BOX/pc")} 2>/dev/null; echo @@; date +%s").out
        val beat = out.substringBefore("@@").trim().split(' ').getOrNull(1)?.toLongOrNull() ?: return null
        val now = out.substringAfter("@@").trim().toLongOrNull() ?: return null
        return (now - beat).coerceAtLeast(0)
    }

    /** True while EasyADB is open on a PC that is connected to this phone with adb. */
    fun connected(): Boolean = (silence() ?: Long.MAX_VALUE) <= FRESH

    fun serial(): String = shell.run("getprop ro.serialno").out.trim()

    /**
     * Gives the PC one job and waits for it to end. Progress goes to [task]. The PC may need the
     * user to allow it first, so a waiting request is not a stuck one: only a PC that stopped
     * looking at the phone ends the wait.
     */
    fun send(request: PcRequest, task: Task): PcStatus {
        if (!connected()) throw AppException(ErrorCode.PC_MISSING)
        shell.run("rm -f ${q("$BOX/status.json")} ${q("$BOX/cancel")}")
        write("request.json", JSON.encodeToString(PcRequest.serializer(), request.copy(created = clock(), phone = serial())))
        task.progress(Stage.TRANSFER)
        var cancelledAt = -1L
        try {
            while (true) {
                if (cancelledAt < 0 && task.cancelled()) {
                    write("cancel", request.id)
                    cancelledAt = clock()
                }
                val status = status(request.id)
                if (status != null) {
                    if (status.state == "running") task.progress(stageOf(status.stage), status.done, status.total)
                    if (status.finished) return settle(status)
                }
                if (cancelledAt >= 0 && clock() - cancelledAt > CANCEL_WAIT) throw AppException(ErrorCode.CANCELLED)
                if ((silence() ?: Long.MAX_VALUE) > LOST) throw AppException(ErrorCode.PC_LOST)
                pause(POLL)
            }
        } finally {
            shell.run("rm -f ${q("$BOX/request.json")} ${q("$BOX/status.json")} ${q("$BOX/cancel")}")
        }
    }

    private fun settle(status: PcStatus): PcStatus = when (status.state) {
        "done" -> status
        "cancelled" -> throw AppException(ErrorCode.CANCELLED)
        "rejected" -> throw AppException(ErrorCode.PC_REJECTED, status.error.orEmpty())
        else -> throw AppException(ErrorCode.PC_FAILED, status.error.orEmpty())
    }

    private fun status(id: String): PcStatus? {
        val text = read("status.json")
        if (text.isEmpty()) return null
        val status = try {
            JSON.decodeFromString(PcStatus.serializer(), text)
        } catch (e: SerializationException) {
            return null
        }
        return status.takeIf { it.id == id }
    }

    /** The app backups that the PC holds, newest list the PC wrote. */
    fun records(): List<PcRecord> {
        val text = read("pc-backups.json")
        if (text.isEmpty()) return emptyList()
        return try {
            JSON.decodeFromString(ListSerializer(PcRecord.serializer()), text)
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    /** A job the PC left for this phone, if it is recent enough to still matter. */
    fun pendingJob(): PcJob? {
        val text = read("job.json")
        if (text.isEmpty()) return null
        val job = try {
            JSON.decodeFromString(PcJob.serializer(), text)
        } catch (e: SerializationException) {
            return null
        }
        val valid = job.format == JOB_FORMAT && job.version == 1 && Regex("[A-Za-z0-9-]{1,64}").matches(job.id) &&
            clock() - job.created in -60..MAX_AGE
        return job.takeIf { valid }
    }

    /** Tells the PC how the job it left is going. */
    fun reportJob(status: PcStatus) {
        write("job-status.json", JSON.encodeToString(PcStatus.serializer(), status))
    }

    /** True when the PC asked to stop the job [id] it left. */
    fun cancelRequested(id: String): Boolean = read("job-cancel") == id

    /** Takes the job off the shelf so that it runs once. */
    fun takeJob() {
        shell.run("rm -f ${q("$BOX/job.json")}")
    }

    companion object {
        const val BOX = "/data/local/tmp/easyandroid"
        const val REQUEST_FORMAT = "easyandroid-request"
        const val JOB_FORMAT = "easyandroid-job"
        private const val FRESH = 10L
        private const val LOST = 20L
        private const val MAX_AGE = 600L
        private const val CANCEL_WAIT = 15L
        private const val POLL = 500L

        fun newId(): String = UUID.randomUUID().toString()

        /** The stage that EasyADB names, as the one EasyAndroid shows. */
        fun stageOf(name: String): Stage = when (name) {
            "apk" -> Stage.APK
            "obb" -> Stage.OBB
            "data" -> Stage.DATA
            "files" -> Stage.FILES
            "verify" -> Stage.VERIFY
            else -> Stage.TRANSFER
        }
    }
}
