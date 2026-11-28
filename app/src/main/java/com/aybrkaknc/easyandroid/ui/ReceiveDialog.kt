package com.aybrkaknc.easyandroid.ui

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.job.JobState
import com.aybrkaknc.easyandroid.job.Jobs
import com.aybrkaknc.easyandroid.job.ResultKind
import com.aybrkaknc.easyandroid.job.arrange
import com.aybrkaknc.easyandroid.job.heading
import com.aybrkaknc.easyandroid.job.summary

private const val SYSTEM_FILES = "com.android.documentsui"

/** Opens [folder] in a file app; null at the storage root, outside the shared storage, or when no app can show it. */
private fun folderIntent(context: Context, folder: String): Intent? {
    val roots = listOf("/sdcard", "/storage/emulated/0")
    val root = roots.firstOrNull { folder == it || folder.startsWith("$it/") } ?: return null
    val authority = "com.android.externalstorage.documents"
    val inside = folder.removePrefix(root).trim('/')
    // The storage root cannot be shown reliably by file apps, so only folders inside it are offered.
    if (inside.isEmpty()) return null
    val uri = DocumentsContract.buildDocumentUri(authority, "primary:$inside")
    // The system Files app opens the folder itself; other apps may open the parent instead, so it comes first.
    val system = Intent(Intent.ACTION_VIEW)
        .setClassName(SYSTEM_FILES, "$SYSTEM_FILES.files.FilesActivity")
        .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (system.resolveActivity(context.packageManager) != null) return system
    val any = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return any.takeIf { it.resolveActivity(context.packageManager) != null }
}

/** Files that a PC is sending to this phone. It can be put away while they arrive. */
@Composable
fun ReceiveDialog(job: JobState) {
    val context = LocalContext.current
    val folder = job.info.folder
    val open = remember(folder) { folder?.let { folderIntent(context, it) } }
    AlertDialog(
        // While files arrive, leaving the window only puts it away.
        onDismissRequest = { if (job.finished) Jobs.dismiss() else Jobs.hide() },
        title = { Text(stringResource(heading(job))) },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (job.finished) {
                    Subject(summary(context, job), job.info, null)
                    val rows = job.results.map { row(it) }
                    val arranged = arrange(rows, { it.kind }, { it.notTried })
                    arranged.problems.forEach { ResultLine(it) }
                    if (arranged.notTried.isNotEmpty()) NotTriedLine(arranged.notTried)
                    arranged.fine.forEach { ResultLine(it) }
                    Hint(stringResource(R.string.job_took, clock(job.seconds)))
                } else {
                    Subject(job.item, job.info, if (job.total > 1) stringResource(R.string.job_place, job.index + 1, job.total) else null)
                    if (job.size > 0) {
                        LinearProgressIndicator(progress = { (job.done.toFloat() / job.size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Amount(job.done, job.size, job.lane)
                    Elapsed(job.startedAt)
                }
            }
        },
        confirmButton = {
            if (job.finished) {
                TextButton({ Jobs.dismiss() }) { Text(stringResource(R.string.close)) }
            } else {
                TextButton({ Jobs.hide() }) { Text(stringResource(R.string.receive_background)) }
            }
        },
        dismissButton = {
            if (!job.finished) {
                TextButton({ Jobs.askCancel(true) }, enabled = !job.cancelling) { Text(stringResource(R.string.cancel)) }
            } else if (open != null && job.results.any { it.kind != ResultKind.FAILED }) {
                TextButton({ runCatching { context.startActivity(open) } }) { Text(stringResource(R.string.receive_open_folder)) }
            }
        },
    )
    if (job.askingCancel && !job.finished) {
        ConfirmDialog(
            title = stringResource(R.string.cancel_receive_title),
            confirm = stringResource(R.string.cancel_confirm),
            onConfirm = { Jobs.cancel() },
            onDismiss = { Jobs.askCancel(false) },
            dismiss = stringResource(R.string.cancel_keep),
        ) { Text(stringResource(R.string.cancel_receive_text)) }
    }
}

/** A thin line for a transfer whose window was put away; a tap brings the window back. */
@Composable
fun ReceiveStrip(job: JobState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable { Jobs.show() }) {
        Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (job.finished) stringResource(heading(job)) else job.item,
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        job.finished -> summary(context, job).takeIf { job.total > 1 }.orEmpty()
                        job.size > 0 -> "${(job.done * 100 / job.size).coerceIn(0, 100)}%"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!job.finished) {
                LinearProgressIndicator(
                    progress = { if (job.size > 0) (job.done.toFloat() / job.size).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
