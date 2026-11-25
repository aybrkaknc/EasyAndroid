package com.aybrkaknc.easyandroid.job

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aybrkaknc.easyandroid.MainActivity
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.PcJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** The user's answer to a job that a PC wants this phone to do. */
enum class PcAnswer { REJECT, ONCE, TRUST, EXPIRED, CANCELLED }

/** A job that waits for the user. [until] is in milliseconds of the system clock. */
data class PcQuestion(val job: PcJob, val until: Long)

/**
 * Asks whether a PC may run a job here: with a notification, which also works while the phone is
 * locked, and with a window while the app is open. The first answer counts.
 */
object PcApproval {
    const val WAIT_MS = 120_000L
    private const val CHANNEL = "approvals"
    private const val ID = 3

    private val flow = MutableStateFlow<PcQuestion?>(null)
    val question: StateFlow<PcQuestion?> = flow

    private var pending: CompletableDeferred<PcAnswer>? = null

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    /** False when only the app itself can show the question. */
    fun canNotify(context: Context): Boolean {
        val manager = manager(context)
        createChannel(context)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun createChannel(context: Context) {
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_approvals), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** Waits for the answer. [cancelled] tells whether the PC took the job back. */
    suspend fun ask(context: Context, job: PcJob, cancelled: () -> Boolean): PcAnswer {
        val answer = CompletableDeferred<PcAnswer>()
        pending = answer
        val until = System.currentTimeMillis() + WAIT_MS
        flow.value = PcQuestion(job, until)
        if (canNotify(context)) manager(context).notify(ID, notification(context, job))
        try {
            while (System.currentTimeMillis() < until) {
                val given = withTimeoutOrNull(1000) { answer.await() }
                if (given != null) return given
                if (cancelled()) return PcAnswer.CANCELLED
            }
            return PcAnswer.EXPIRED
        } finally {
            pending = null
            flow.value = null
            manager(context).cancel(ID)
        }
    }

    /** Only an answer to the question that is open counts. */
    fun answer(id: String, answer: PcAnswer) {
        if (flow.value?.job?.id == id) pending?.complete(answer)
    }

    private fun notification(context: Context, job: PcJob): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        fun reply(code: Int, action: String) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, PcApprovalReceiver::class.java).setAction(action).putExtra(PcApprovalReceiver.JOB, job.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val reject = Notification.Action.Builder(null, context.getString(R.string.pc_ask_reject), reply(1, PcApprovalReceiver.REJECT)).build()
        // A locked phone must be unlocked before it says yes. Where Android cannot ask for that, yes opens the app.
        val accept = if (Build.VERSION.SDK_INT >= 31) {
            Notification.Action.Builder(null, context.getString(R.string.pc_ask_accept), reply(2, PcApprovalReceiver.ACCEPT))
                .setAuthenticationRequired(true).build()
        } else {
            Notification.Action.Builder(null, context.getString(R.string.pc_ask_accept), open).build()
        }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_restore)
            .setContentTitle(context.getString(R.string.pc_ask_title, pcName(context, job)))
            .setContentText(what(context, job))
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_CALL)
            .setTimeoutAfter(WAIT_MS)
            .addAction(reject)
            .addAction(accept)
            .build()
    }

    fun pcName(context: Context, job: PcJob): String =
        job.pcName.filter { !it.isISOControl() }.take(40).ifBlank { context.getString(R.string.pc_ask_unknown) }

    /** What the PC wants, in words. */
    fun what(context: Context, job: PcJob): String {
        val kinds = when (job.type) {
            "system" -> listOf(R.string.tab_system)
            else -> job.kinds.mapNotNull {
                when (it) {
                    "messages" -> R.string.chip_messages
                    "calls" -> R.string.chip_calls
                    "wallpaper" -> R.string.chip_wallpapers
                    else -> null
                }
            }
        }
        return context.getString(R.string.pc_ask_text, kinds.joinToString(", ") { context.getString(it) })
    }
}

/** The buttons of the notification. Not exported, so only this app's notification reaches it. */
class PcApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(JOB) ?: return
        when (intent.action) {
            ACCEPT -> PcApproval.answer(id, PcAnswer.ONCE)
            REJECT -> PcApproval.answer(id, PcAnswer.REJECT)
        }
    }

    companion object {
        const val ACCEPT = "com.aybrkaknc.easyandroid.PC_ACCEPT"
        const val REJECT = "com.aybrkaknc.easyandroid.PC_REJECT"
        const val JOB = "job"
    }
}
