package com.aybrkaknc.easyandroid

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.ui.AppTheme
import com.aybrkaknc.easyandroid.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start, so that turning the screen does not ask again.
        if (savedInstanceState == null) open(intent)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                MainScreen(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    /** Brings up the incoming transfer that a notification was about. */
    private fun open(intent: Intent) {
        val cancel = intent.getBooleanExtra(ASK_CANCEL, false)
        if (cancel || intent.getBooleanExtra(SHOW_JOB, false)) Jobs.show()
        if (cancel) Jobs.askCancel(true)
    }

    companion object {
        const val SHOW_JOB = "show_job"
        const val ASK_CANCEL = "ask_cancel"
    }
}
