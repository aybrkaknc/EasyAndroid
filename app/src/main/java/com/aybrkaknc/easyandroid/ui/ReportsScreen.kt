package com.aybrkaknc.easyandroid.ui

import com.aybrkaknc.easyandroid.job.arrange
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.AppLog
import com.aybrkaknc.easyandroid.job.Report
import com.aybrkaknc.easyandroid.job.ReportLog
import com.aybrkaknc.easyandroid.job.ResultKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends the end of the app log to another app, for finding what went wrong. */
private fun shareLog(context: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AppLog.tail().ifBlank { "-" })
    context.startActivity(Intent.createChooser(send, null))
}

/** The results of the last jobs, newest first. Problems come first inside a report. */
@Composable
fun ReportsScreen() {
    val context = LocalContext.current
    var reports by remember { mutableStateOf<List<Report>?>(null) }
    var open by remember { mutableStateOf<Int?>(null) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { reports = withContext(Dispatchers.IO) { ReportLog.read(context) } }

    val list = reports ?: return Loading()
    if (list.isEmpty()) return Empty(stringResource(R.string.reports_empty))

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(
                stringResource(R.string.advanced_reports_text), Modifier.weight(1f).padding(start = 4.dp, top = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton({ shareLog(context) }) { Text(stringResource(R.string.reports_share_log)) }
            TextButton({ clearing = true }) { Text(stringResource(R.string.reports_clear)) }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(list) { index, report ->
                Column(
                    Modifier.fillMaxWidth().clip(groupShape(index, list.size)).background(cardColor())
                        .clickable { open = if (open == index) null else index }.padding(16.dp),
                ) {
                    Text(report.title, style = MaterialTheme.typography.titleMedium)
                    Text(formatDate(report.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(
                            if (report.cancelled) R.string.job_summary_cancelled else R.string.job_summary,
                            report.done, report.total, report.failed,
                        ),
                        color = if (report.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (open == index) {
                        val rows = report.items.map { ResultRow(it.name, ResultKind.valueOf(it.kind), it.lines, it.notTried) }
                        val arranged = arrange(rows, { it.kind }, { it.notTried })
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            arranged.problems.forEach { ResultLine(it) }
                            if (arranged.notTried.isNotEmpty()) NotTriedLine(arranged.notTried)
                            arranged.fine.forEach { ResultLine(it) }
                        }
                    }
                }
            }
        }
    }

    if (clearing) {
        ConfirmDialog(
            title = stringResource(R.string.reports_clear_title),
            confirm = stringResource(R.string.reports_clear),
            onConfirm = {
                clearing = false
                ReportLog.clear(context)
                reports = emptyList()
            },
            onDismiss = { clearing = false },
        ) { Text(stringResource(R.string.reports_clear_text)) }
    }
}
