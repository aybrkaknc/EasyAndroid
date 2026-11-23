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

/** Keeps the process alive while a job runs and shows its progress. */
class JobService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_jobs), NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(PROGRESS, notification(Jobs.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            while (true) {
                val state = Jobs.state.value
                if (state == null || state.finished) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (state != null) manager.notify(RESULT, notification(state))
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

    private fun notification(state: JobState?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_restore)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
        if (state == null) return builder.setContentTitle(getString(R.string.app_name)).build()
        builder.setContentTitle(getString(state.title))
        if (state.finished) {
            val failed = state.results.count { it.kind == ResultKind.FAILED }
            return builder.setAutoCancel(true)
                .setContentText(getString(R.string.job_summary, state.results.size - failed, state.total, failed)).build()
        }
        return builder.setOngoing(true)
            .setContentText(getString(R.string.job_position, state.index + 1, state.total, state.item))
            .setProgress(state.total, state.index, false).build()
    }

    private companion object {
        const val CHANNEL = "jobs"
        const val PROGRESS = 1
        const val RESULT = 2
    }
}
