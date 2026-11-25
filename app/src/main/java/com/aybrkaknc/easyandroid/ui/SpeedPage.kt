package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppException
import com.aybrkaknc.easyandroid.core.ErrorCode
import com.aybrkaknc.easyandroid.core.PcLink
import com.aybrkaknc.easyandroid.core.PcRequest
import com.aybrkaknc.easyandroid.core.Stage
import com.aybrkaknc.easyandroid.core.Task
import com.aybrkaknc.easyandroid.job.UiText
import com.aybrkaknc.easyandroid.job.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** How much goes each way. Enough to settle on a speed, short enough to wait for. */
private const val MEGABYTES = 32

private sealed interface SpeedState {
    data object Idle : SpeedState

    /** [item] is the direction the PC works on now. Empty until it starts. [since] is when it began. */
    /** [rate] is the speed over the last second or so, in megabytes per second. */
    data class Running(
        val item: String = "",
        val done: Long = 0,
        val total: Long = 0,
        val since: Long = 0,
        val rate: Double = 0.0,
        val sampleDone: Long = 0,
        val sampleAt: Long = 0,
        val queued: Boolean = false,
    ) : SpeedState
    data class Done(val toPc: Double?, val toPhone: Double?) : SpeedState
    data class Failed(val text: UiText) : SpeedState
}

/** Notes of the PC look like `speed_to_pc:12.34`, in megabytes per second. */
private fun List<String>.speed(name: String): Double? =
    firstOrNull { it.startsWith("$name:") }?.substringAfter(':')?.toDoubleOrNull()

/** A log line with the time since [from], as mm:ss. */
private fun stamp(from: Long, text: String): String {
    val seconds = (System.currentTimeMillis() - from) / 1000
    return "%02d:%02d  %s".format(seconds / 60, seconds % 60, text)
}

/** Measures how fast data travels between this phone and the PC that EasyADB runs on. */
@Composable
fun SpeedPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cancel = remember { AtomicBoolean(false) }
    val connected = rememberPcConnected()
    var state by remember { mutableStateOf<SpeedState>(SpeedState.Idle) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val locale = LocalConfiguration.current.locales[0]
    val running = state is SpeedState.Running

    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    fun start() {
        cancel.set(false)
        startedAt = System.currentTimeMillis()
        now = startedAt
        log = listOf(stamp(startedAt, Env.app.getString(R.string.speed_log_request)))
        state = SpeedState.Running()
        fun fail(text: UiText): SpeedState {
            log = log + stamp(startedAt, text.resolve(context))
            return SpeedState.Failed(text)
        }
        scope.launch(Dispatchers.IO) {
            val task = Task({ cancel.get() }, { item ->
                val current = state as? SpeedState.Running
                if (current != null && item.isNotEmpty() && item != current.item) {
                    val line = if (item == "to_pc") R.string.speed_log_to_pc else R.string.speed_log_to_phone
                    log = log + stamp(startedAt, Env.app.getString(line))
                    state = SpeedState.Running(item, 0, 0, System.currentTimeMillis())
                }
            }) { stage, done, total ->
                val current = state as? SpeedState.Running ?: return@Task
                val now = System.currentTimeMillis()
                var next = current.copy(done = done, total = total, queued = stage == Stage.QUEUED)
                if (current.sampleAt == 0L) {
                    next = next.copy(sampleDone = done, sampleAt = now)
                } else if (now - current.sampleAt >= 500) {
                    val seconds = (now - current.sampleAt) / 1000.0
                    next = next.copy(rate = (done - current.sampleDone) / 1_000_000.0 / seconds, sampleDone = done, sampleAt = now)
                }
                state = next
            }
            state = try {
                val request = PcRequest(id = PcLink.newId(), created = 0, type = "speed", megabytes = MEGABYTES)
                val notes = Env.pc().send(request, task).notes
                log = log + stamp(startedAt, Env.app.getString(R.string.speed_log_finished))
                SpeedState.Done(notes.speed("speed_to_pc"), notes.speed("speed_to_phone"))
            } catch (e: AppException) {
                if (e.code == ErrorCode.CANCELLED) {
                    log = log + stamp(startedAt, Env.app.getString(R.string.speed_log_cancelled))
                    SpeedState.Idle
                } else {
                    fail(errorText(e))
                }
            } catch (e: Exception) {
                fail(errorText(e))
            }
        }
    }

    @Composable
    fun result(title: Int, mbPerSecond: Double?) {
        Text(stringResource(title), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        Text(
            if (mbPerSecond == null) "-" else stringResource(
                R.string.speed_result, "%.1f".format(locale, mbPerSecond), "%.0f".format(locale, mbPerSecond * 8),
            ),
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
        )
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.speed_intro, MEGABYTES), style = MaterialTheme.typography.bodyMedium)
        if (!connected && !running) {
            Text(
                stringResource(R.string.target_pc_missing), Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val current = state) {
            is SpeedState.Running -> {
                Text(
                    stringResource(R.string.speed_elapsed, ((now - startedAt) / 1000).toInt()),
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall,
                )
                if (current.item.isEmpty()) {
                    Text(stringResource(if (current.queued) R.string.stage_queued else R.string.speed_waiting), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                } else {
                    val title = if (current.item == "to_pc") R.string.speed_to_pc else R.string.speed_to_phone
                    Text(stringResource(title), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                    val percent = if (current.total > 0) (current.done * 100 / current.total).toInt() else 0
                    val elapsed = now - current.since
                    val leftMs = if (current.done > 0 && elapsed > 0 && current.total > current.done) {
                        (current.total - current.done) * elapsed / current.done
                    } else {
                        null
                    }
                    val doneMb = (current.done / 1_000_000).toInt()
                    val totalMb = (current.total / 1_000_000).toInt()
                    if (current.rate > 0) {
                        Text(
                            stringResource(R.string.speed_now, "%.1f".format(locale, current.rate)),
                            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        if (leftMs == null) {
                            stringResource(R.string.speed_progress_plain, percent, doneMb, totalMb)
                        } else {
                            stringResource(R.string.speed_progress, percent, doneMb, totalMb, (leftMs / 1000).toInt())
                        },
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                    if (current.total > 0) {
                        LinearProgressIndicator(
                            progress = { (current.done.toFloat() / current.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                }
                OutlinedButton({ cancel.set(true) }) { Text(stringResource(R.string.cancel)) }
            }
            is SpeedState.Done -> {
                result(R.string.speed_to_pc, current.toPc)
                result(R.string.speed_to_phone, current.toPhone)
            }
            is SpeedState.Failed -> Text(
                current.text.resolve(context),
                Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error,
            )
            SpeedState.Idle -> {}
        }
        if (!running) {
            Button({ start() }, Modifier.fillMaxWidth().padding(top = 24.dp), enabled = connected) {
                Text(stringResource(R.string.speed_start))
            }
        }
        if (log.isNotEmpty()) {
            Text(stringResource(R.string.speed_log_title), Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleMedium)
            log.forEach {
                Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
