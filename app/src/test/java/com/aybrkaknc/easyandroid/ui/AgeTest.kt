package com.aybrkaknc.easyandroid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AgeTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")

    private fun ago(span: Duration) = ageOf(now - span, now)

    @Test
    fun picksTheLargestUnit() {
        assertEquals(Age(0, AgeUnit.NOW), ago(Duration.ofSeconds(59)))
        assertEquals(Age(1, AgeUnit.MINUTES), ago(Duration.ofSeconds(60)))
        assertEquals(Age(59, AgeUnit.MINUTES), ago(Duration.ofMinutes(59).plusSeconds(59)))
        assertEquals(Age(1, AgeUnit.HOURS), ago(Duration.ofMinutes(60)))
        assertEquals(Age(23, AgeUnit.HOURS), ago(Duration.ofHours(23).plusMinutes(59)))
        assertEquals(Age(1, AgeUnit.DAYS), ago(Duration.ofHours(24)))
        assertEquals(Age(29, AgeUnit.DAYS), ago(Duration.ofDays(29).plusHours(23)))
        assertEquals(Age(1, AgeUnit.MONTHS), ago(Duration.ofDays(30)))
        assertEquals(Age(12, AgeUnit.MONTHS), ago(Duration.ofDays(364)))
        assertEquals(Age(1, AgeUnit.YEARS), ago(Duration.ofDays(365)))
        assertEquals(Age(2, AgeUnit.YEARS), ago(Duration.ofDays(800)))
    }

    @Test
    fun aTimeInTheFutureCountsAsNow() {
        assertEquals(Age(0, AgeUnit.NOW), ageOf(now + Duration.ofDays(3), now))
    }

    @Test
    fun oldBackupsAreThirtyDaysAndMore() {
        assertFalse(isStale(now - Duration.ofDays(29).plusHours(23), now))
        assertTrue(isStale(now - Duration.ofDays(30), now))
        assertTrue(isStale(now - Duration.ofDays(400), now))
        assertFalse(isStale(now + Duration.ofDays(1), now))
    }

    @Test
    fun readsTheTimeOfSystemBackupFiles() {
        val time = systemBackupTime("system_20261125-143005.json", ZoneOffset.UTC)
        assertEquals(Instant.parse("2026-11-25T14:30:05Z"), time)
        assertEquals(time, systemBackupTime("system_20261125-143005-2.json", ZoneOffset.UTC))
        for (bad in listOf("system_20261125.json", "system_20261399-250000.json", "other_20261125-143005.json", "system_20261125-143005.txt", "")) {
            assertNull(bad, systemBackupTime(bad, ZoneOffset.UTC))
        }
    }
}
