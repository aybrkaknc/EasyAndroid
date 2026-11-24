package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aybrkaknc.easyandroid.Env
import com.aybrkaknc.easyandroid.R

/** A quick action as a card, on Home ([pinned] null) or in the full list. */
@Composable
fun QuickActionCard(
    action: QuickAction,
    shape: Shape,
    enabled: Boolean,
    pinned: Boolean?,
    onAction: (QuickAction) -> Unit,
    onEasyAdb: () -> Unit,
) {
    val context = LocalContext.current
    val device = when (action.buttons) {
        QuickButtons.NONE -> null
        QuickButtons.TO -> R.string.to_device
        QuickButtons.FROM, QuickButtons.DELETE -> R.string.from_device
    }
    QuickCard(
        title = stringResource(action.title),
        text = stringResource(action.text),
        shape = shape,
        enabled = enabled || action.soon,
        soon = action.soon,
        root = action.root,
        danger = action.buttons == QuickButtons.DELETE,
        buttons = if (device == null) emptyList() else listOf(
            CardButton(device) { onAction(action) },
            CardButton(R.string.with_easyadb, onEasyAdb),
        ),
        pinned = pinned,
        onPin = { Env.prefs.togglePinned(action.id) },
        onClick = when {
            action.soon -> ({ showSoon(context) })
            device == null -> ({ onAction(action) })
            else -> null
        },
    )
}

/** Every quick action. Pinned ones also show on Home. */
@Composable
fun QuickActionsScreen(enabled: Boolean, onAction: (QuickAction) -> Unit, onEasyAdb: () -> Unit) {
    val settings by Env.prefs.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
        QuickSection.entries.forEach { section ->
            SectionLabel(painterResource(R.drawable.ic_android), stringResource(section.title), section.hint?.let { stringResource(it) })
            val actions = QuickAction.entries.filter { it.section == section }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                actions.forEachIndexed { index, action ->
                    QuickActionCard(action, groupShape(index, actions.size), enabled, action.id in settings.pinned, onAction, onEasyAdb)
                }
            }
        }
    }
}
