package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.job.PcAnswer
import com.aybrkaknc.easyandroid.job.PcApproval
import com.aybrkaknc.easyandroid.job.PcQuestion
import kotlinx.coroutines.delay

/** Asks whether a PC may run a job on this phone. Only its buttons close it. */
@Composable
fun PcQuestionDialog(question: PcQuestion) {
    val context = LocalContext.current
    val job = question.job
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(question) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = ((question.until - now) / 1000).coerceAtLeast(0).toInt()
    fun answer(value: PcAnswer) = PcApproval.answer(job.id, value)
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.pc_ask_title, PcApproval.pcName(context, job))) },
        text = {
            Column {
                Text(PcApproval.what(context, job))
                Text(
                    stringResource(R.string.pc_ask_hint), Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.pc_ask_left, left / 60, left % 60), Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        confirmButton = {
            Column {
                TextButton({ answer(PcAnswer.ONCE) }) { Text(stringResource(R.string.pc_ask_once)) }
                // A PC that does not say who it is cannot be recognised again.
                if (job.pc.isNotEmpty()) TextButton({ answer(PcAnswer.TRUST) }) { Text(stringResource(R.string.pc_ask_trust)) }
            }
        },
        dismissButton = { TextButton({ answer(PcAnswer.REJECT) }) { Text(stringResource(R.string.pc_ask_reject)) } },
    )
}

/** The PCs whose jobs start without asking. */
@Composable
fun TrustedPcsPage() {
    val settings by Env.prefs.state.collectAsStateWithLifecycle()
    val pcs = settings.trustedPcs
    if (pcs.isEmpty()) return Empty(stringResource(R.string.pcs_empty))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        CardGroup(pcs.size) { index, shape ->
            SettingCard(pcs[index].name, shape, summary = pcs[index].id.take(8), onClick = {}) {
                TextButton({ Env.prefs.forgetPc(pcs[index].id) }) { Text(stringResource(R.string.pcs_forget)) }
            }
        }
        Hint(stringResource(R.string.pcs_hint))
    }
}
