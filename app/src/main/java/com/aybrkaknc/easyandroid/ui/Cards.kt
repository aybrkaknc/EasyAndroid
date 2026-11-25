package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    summaryColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxWidth().clip(shape).background(if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else cardColor()),
    ) {
        SettingRow(title, summary, enabled, onClick, icon, tint, summaryColor, trailing = trailing)
    }
}

/** The cards of one settings group, shaped like one block. */
@Composable
fun CardGroup(count: Int, horizontal: Dp = 12.dp, row: @Composable (index: Int, shape: Shape) -> Unit) {
    Column(Modifier.padding(horizontal = horizontal), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) { row(it, groupShape(it, count)) }
    }
}
