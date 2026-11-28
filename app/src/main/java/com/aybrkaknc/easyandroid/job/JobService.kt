package com.aybrkaknc.easyandroid.job

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.aybrkaknc.easyandroid.MainActivity
import com.aybrkaknc.easyandroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Keeps the process alive while a job runs and shows its progress. */
class JobService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_jobs), NotificationManager.IMPORTANCE_LOW),
        )
        // A finished job is news. Problems come down from the top of the screen; the rest stays in the shade.
        manager.createNotificationChannel(
            NotificationChannel(RESULTS, getString(R.string.channel_results), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(PROBLEMS, getString(R.string.channel_problems), NotificationManager.IMPORTANCE_HIGH),
        )
        startForeground(PROGRESS, notification(Jobs.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            // EasyADB starts this service to hand over a job; it is read before the service decides it is idle.
            withContext(Dispatchers.IO) { runCatching { PcJobs.runPending(applicationContext) } }
            while (true) {
                val state = Jobs.state.value
                if (state == null || state.finished) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (state != null) {
                        val problem = outcomeOf(state.cancelled, state.results).let { it == Outcome.WARNING || it == Outcome.INCOMPLETE }
                        manager.notify(RESULT, notification(state, if (problem) PROBLEMS else RESULTS))
                    }
                    stopSelf()
                    break
                }
                manager.notify(PROGRESS, notification(state))
                delay(700)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
    }

    /** Opens the app; [extra] tells it what to bring up. */
    private fun activity(code: Int, extra: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        extra?.let { intent.putExtra(it, true) }
        return PendingIntent.getActivity(this, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun notification(state: JobState?, channel: String = CHANNEL): Notification {
        val receiving = state != null && jobKind(state.title) == JobKind.RECEIVE
        val open = if (receiving) activity(1, MainActivity.SHOW_JOB) else activity(0, null)
        val builder = Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_restore)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
        if (state == null) return builder.setContentTitle(getString(R.string.app_name)).build()
        builder.setContentTitle(getString(heading(state)))
        if (state.finished) return builder.setAutoCancel(true).setContentText(summary(this, state)).build()
        if (receiving) return transfer(builder, state)
        return builder.setOngoing(true)
            .setContentText(getString(R.string.job_notification, state.item, state.index + 1, state.total))
            .setProgress(state.total, state.index, false).build()
    }

    /** An incoming transfer: how far its file is, how fast it comes and a way to stop it. */
    private fun transfer(builder: Notification.Builder, state: JobState): Notification {
        val percent = if (state.size > 0) (state.done * 100 / state.size).coerceIn(0, 100).toInt() else 0
        val rate = state.lane?.takeIf { it.rate > 0 }?.let { String.format(Locale.getDefault(), "%.1f", it.rate / 1_000_000) }
        val left = state.lane?.secondsLeft?.let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) }
        val cancel = Notification.Action.Builder(null, getString(R.string.cancel), activity(2, MainActivity.ASK_CANCEL)).build()
        return builder.setOngoing(true)
            .setContentText(getString(R.string.job_notification, state.item, state.index + 1, state.total))
            .setSubText("$percent% · " + getString(R.string.job_rate_left, rate ?: "-", left ?: "-"))
            .setProgress(100, percent, state.size <= 0)
            .addAction(cancel)
            .build()
    }

    private companion object {
        const val CHANNEL = "jobs"
        const val RESULTS = "results"
        const val PROBLEMS = "problems"
        const val PROGRESS = 1
        const val RESULT = 2
    }
}
