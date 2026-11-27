package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.job.ResultKind

/** One line of what a job did, with the words already in the language of the app. */
data class ResultRow(val name: String, val kind: ResultKind, val lines: List<String>, val notTried: Boolean = false)

/** An item that ended: a mark, its name and what there is to say about it. */
@Composable
fun ResultLine(row: ResultRow) {
    // Long messages are cut so that the list stays readable; a tap shows all of it.
    var open by rememberSaveable(row.name) { mutableStateOf(false) }
    val color = when (row.kind) {
        ResultKind.OK -> MaterialTheme.colorScheme.primary
        ResultKind.WARNING -> MaterialTheme.colorScheme.tertiary
        ResultKind.FAILED -> MaterialTheme.colorScheme.error
    }
    val mark = when (row.kind) {
        ResultKind.OK -> "✓"
        ResultKind.WARNING -> "!"
        ResultKind.FAILED -> "✕"
    }
    val status = stringResource(
        when (row.kind) {
            ResultKind.OK -> R.string.result_ok
            ResultKind.WARNING -> R.string.result_warning
            ResultKind.FAILED -> R.string.result_failed
        },
    )
    val spoken = stringResource(R.string.result_line, row.name, status)
    Column(Modifier.fillMaxWidth().clickable(enabled = row.lines.isNotEmpty()) { open = !open }.padding(vertical = 2.dp)) {
        Text("$mark  ${row.name}", Modifier.semantics { contentDescription = spoken }, color = color)
        row.lines.forEach {
            Text(
                it, Modifier.padding(start = 20.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Everything that was never tried, as one line; a tap names them. */
@Composable
fun NotTriedLine(rows: List<ResultRow>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("○  " + stringResource(R.string.not_tried_count, rows.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) rows.forEach { Text(it.name, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodySmall) }
    }
}
