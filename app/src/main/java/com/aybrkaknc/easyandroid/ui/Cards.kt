package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aybrkaknc.easyandroid.R

private const val DIM = 0.4f

/** Always a little off the background. Some ROMs make the surface containers of the theme darker than it. */
@Composable
fun cardColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.background)

/** Cards of one section look like one block: large corners outside, small ones between. */
fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) 28.dp else 6.dp
    val bottom = if (index == count - 1) 28.dp else 6.dp
    return RoundedCornerShape(top, top, bottom, bottom)
}

/** Small capital letters above a value or a section. */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier,
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SectionLabel(icon: Painter, title: String, hint: String? = null) {
    Row(
        Modifier.padding(start = 12.dp, top = 32.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            CapsLabel(title)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

class CardButton(@param:StringRes val text: Int, val onClick: () -> Unit)

/** One action. */
@Composable
fun QuickCard(
    title: String,
    text: String,
    shape: Shape,
    enabled: Boolean = true,
    root: Boolean = false,
    danger: Boolean = false,
    buttons: List<CardButton> = emptyList(),
    pinned: Boolean? = null,
    onPin: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val dim = if (enabled) Modifier else Modifier.alpha(DIM)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(cardColor())
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(Modifier.padding(top = 4.dp)) {
            Column(Modifier.weight(1f).padding(top = 8.dp).then(dim)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (root) {
                Text(
                    "#", Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp).then(dim),
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                )
            }
            if (pinned != null) {
                IconButton(onPin, Modifier.size(40.dp)) {
                    Icon(
                        painterResource(if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_outline),
                        stringResource(if (pinned) R.string.unpin else R.string.pin),
                        Modifier.size(20.dp).rotate(45f),
                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (buttons.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp).then(dim),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val colors = if (danger) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                }
                buttons.forEach { button ->
                    // Dimming comes from the row, so the button keeps its own colors.
                    FilledTonalButton({ if (enabled) button.onClick() }, Modifier.weight(1f), colors = colors) {
                        Text(stringResource(button.text), textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A settings row inside a group of cards. */
@Composable
fun SettingCard(
    title: String,
    shape: Shape,
    summary: String? = null,
    icon: Painter? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxWidth().clip(shape).background(if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else cardColor()),
    ) {
        SettingRow(title, summary, enabled, onClick, icon, tint, trailing)
    }
}

/** The cards of one settings group, shaped like one block. */
@Composable
fun CardGroup(count: Int, row: @Composable (index: Int, shape: Shape) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) { row(it, groupShape(it, count)) }
    }
}

/** A pill in the color of its category. */
@Composable
fun CategoryChip(icon: Painter, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(color.copy(alpha = 0.1f)).border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(color.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(
            text, Modifier.padding(start = 8.dp, end = 12.dp),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
