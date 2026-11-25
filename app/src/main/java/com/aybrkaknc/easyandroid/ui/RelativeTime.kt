package com.aybrkaknc.easyandroid.ui

import android.icu.text.RelativeDateTimeFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant

/** "2 days ago", in the language of the app. */
fun RelativeDateTimeFormatter.since(then: Instant, now: Instant): String {
    val age = ageOf(then, now)
    val unit = when (age.unit) {
        AgeUnit.NOW -> return format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        AgeUnit.MINUTES -> RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
        AgeUnit.HOURS -> RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
        AgeUnit.DAYS -> RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
        AgeUnit.MONTHS -> RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH
        AgeUnit.YEARS -> RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR
    }
    return formatNumeric(-age.count.toDouble(), unit)
}

@Composable
fun rememberRelativeFormatter(): RelativeDateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { RelativeDateTimeFormatter.getInstance(locale) }
}
