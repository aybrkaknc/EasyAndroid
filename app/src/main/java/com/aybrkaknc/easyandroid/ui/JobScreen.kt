package com.aybrkaknc.easyandroid.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.job.ItemInfo
import com.aybrkaknc.easyandroid.job.JobKind
import com.aybrkaknc.easyandroid.job.JobResult
import com.aybrkaknc.easyandroid.job.JobState
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.Lane
import com.aybrkaknc.easyandroid.job.Outcome
import com.aybrkaknc.easyandroid.job.StepState
import com.aybrkaknc.easyandroid.job.arrange
import com.aybrkaknc.easyandroid.job.heading
import com.aybrkaknc.easyandroid.job.jobKind
import com.aybrkaknc.easyandroid.job.outcomeOf
import com.aybrkaknc.easyandroid.job.stageId
import com.aybrkaknc.easyandroid.job.stepStates
import com.aybrkaknc.easyandroid.job.summary
import kotlinx.coroutines.delay
import java.util.Locale

/** The job that runs, or what it did once it has ended. */
@Composable
fun JobScreen(job: JobState) {
    BackHandler(enabled = job.finished) { Jobs.dismiss() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (job.finished) Ended(job) else Running(job)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Running(job: JobState) {
    Text(stringResource(heading(job)), style = MaterialTheme.typography.headlineSmall)
    Subject(job.item, job.info, if (job.total > 1) stringResource(R.string.job_place, job.index + 1, job.total) else null)
    Steps(job)
    if (job.size > 0) {
        LinearProgressIndicator(progress = { (job.done.toFloat() / job.size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Amount(job.done, job.size, job.lane)
    Elapsed(job.startedAt)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (job.results.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                Hint(stringResource(R.string.job_done_list, job.results.size))
            }
        }
        // The newest first, so that what just happened is seen without scrolling.
        items(job.results.asReversed()) { ResultLine(row(it)) }
    }
    CancelButton(job)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Ended(job: JobState) {
    val context = LocalContext.current
    val color = when (outcomeOf(job.cancelled, job.results)) {
        Outcome.DONE -> MaterialTheme.colorScheme.primary
        Outcome.WARNING -> MaterialTheme.colorScheme.tertiary
        Outcome.INCOMPLETE -> MaterialTheme.colorScheme.error
        Outcome.CANCELLED -> MaterialTheme.colorScheme.onSurface
    }
    Text(stringResource(heading(job)), style = MaterialTheme.typography.headlineSmall, color = color)
    Subject(summary(context, job), job.info.copy(packageName = job.info.packageName.takeIf { job.total == 1 }), null)
    val rows = job.results.map { row(it) }
    val arranged = arrange(rows, { it.kind }, { it.notTried })
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(arranged.problems) { ResultLine(it) }
        if (arranged.notTried.isNotEmpty()) item { NotTriedLine(arranged.notTried) }
        items(arranged.fine) { ResultLine(it) }
    }
    Hint(stringResource(R.string.job_took, clock(job.seconds)))
    if (job.retryable) {
        OutlinedButton({ Jobs.retry() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.job_retry)) }
    }
    Button({ Jobs.dismiss() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
}

@Composable
private fun row(result: JobResult): ResultRow {
    val context = LocalContext.current
    return ResultRow(result.name, result.kind, result.lines.map { it.resolve(context) }, result.notTried)
}

/** The second and third headings: what the page is about, and where it goes or comes from. */
@Composable
private fun Subject(name: String, info: ItemInfo, position: String?) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            info.packageName?.let { AppIcon(it) }
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            position?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
        val detail = info.detail?.resolve(context) ?: info.createdAt?.let { formatDate(it) }
        detail?.let { Hint(it) }
    }
}

/** The parts of an app backup, or the one step that other jobs are at. */
@Composable
private fun Steps(job: JobState) {
    val parts = job.info.parts
    // Waiting for the PC and preparing are not parts of the app, so they get a line of their own.
    if (jobKind(job.title) != JobKind.BACKUP || job.stage !in parts) {
        Text(stringResource(stageId(job.stage)), color = MaterialTheme.colorScheme.primary)
    }
    if (parts.isEmpty()) return
    val states = stepStates(parts, job.stage, job.seen)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.zip(states).forEach { (part, state) ->
            val text = stringResource(stageId(part))
            when (state) {
                StepState.DONE -> Text("✓  $text", color = MaterialTheme.colorScheme.primary)
                StepState.RUNNING -> Text("◉  $text", color = MaterialTheme.colorScheme.primary)
                StepState.WAITING -> Text("○  $text", color = MaterialTheme.colorScheme.onSurfaceVariant)
                StepState.ABSENT -> Text(
                    "○  " + stringResource(R.string.step_absent, text),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Always two lines, so that nothing jumps; what is not known yet shows as a dash. */
@Composable
private fun Amount(done: Long, size: Long, lane: Lane?) {
    Hint(
        if (size <= 0) UNKNOWN
        else stringResource(R.string.job_amount, (done * 100 / size).coerceIn(0, 100).toInt(), formatSize(done), formatSize(size)),
    )
    val rate = lane?.takeIf { it.rate > 0 }?.let { String.format(Locale.getDefault(), "%.1f", it.rate / 1_000_000) }
    val left = lane?.secondsLeft?.let { clock(it) }
    Hint(stringResource(R.string.job_rate_left, rate ?: UNKNOWN, left ?: UNKNOWN))
}

private const val UNKNOWN = "-"

@Composable
private fun Elapsed(startedAt: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    Hint(stringResource(R.string.job_elapsed, clock((now - startedAt) / 1000)))
}

private fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun CancelButton(job: JobState) {
    // A backup or a restore that is cut off does harm, so cancelling one is asked about first.
    var asking by remember { mutableStateOf(false) }
    val question = when (jobKind(job.title)) {
        JobKind.BACKUP -> R.string.cancel_backup_title
        JobKind.RESTORE -> R.string.cancel_restore_title
        else -> null
    }
    OutlinedButton(
        { if (question == null) Jobs.cancel() else asking = true },
        Modifier.fillMaxWidth(), enabled = !job.cancelling,
    ) {
        if (job.cancelling) CircularProgressIndicator() else Text(stringResource(R.string.cancel))
    }
    if (asking && question != null) {
        ConfirmDialog(
            title = stringResource(question),
            confirm = stringResource(R.string.cancel_confirm),
            onConfirm = {
                asking = false
                Jobs.cancel()
            },
            onDismiss = { asking = false },
            dismiss = stringResource(R.string.cancel_keep),
        ) { Text(stringResource(R.string.cancel_confirm_text)) }
    }
}
