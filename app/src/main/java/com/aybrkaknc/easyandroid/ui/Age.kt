package com.aybrkaknc.easyandroid.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AgeUnit { NOW, MINUTES, HOURS, DAYS, MONTHS, YEARS }

/** How long ago something was, in the largest unit that is at least one. */
data class Age(val count: Int, val unit: AgeUnit)

/** A backup older than this many days is shown as old. */
const val STALE_DAYS = 30L

/** A time in the future, e.g. after the clock was set back, counts as now. */
fun ageOf(then: Instant, now: Instant = Instant.now()): Age {
    val span = Duration.between(then, now)
    val days = span.toDays()
    return when {
        span.toMinutes() < 1 -> Age(0, AgeUnit.NOW)
        span.toMinutes() < 60 -> Age(span.toMinutes().toInt(), AgeUnit.MINUTES)
        span.toHours() < 24 -> Age(span.toHours().toInt(), AgeUnit.HOURS)
        days < 30 -> Age(days.toInt(), AgeUnit.DAYS)
        days < 365 -> Age((days / 30).toInt(), AgeUnit.MONTHS)
        else -> Age((days / 365).toInt(), AgeUnit.YEARS)
    }
}

fun isStale(then: Instant, now: Instant = Instant.now()): Boolean = Duration.between(then, now).toDays() >= STALE_DAYS

private val SYSTEM_NAME = Regex("system_(\\d{8}-\\d{6})(-\\d+)?\\.json")
private val SYSTEM_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** The time in the name of a system backup file, so that the files do not have to be opened. */
fun systemBackupTime(name: String, zone: ZoneId = ZoneId.systemDefault()): Instant? {
    val text = SYSTEM_NAME.matchEntire(name)?.groupValues?.get(1) ?: return null
    return runCatching { LocalDateTime.parse(text, SYSTEM_TIME).atZone(zone).toInstant() }.getOrNull()
}
