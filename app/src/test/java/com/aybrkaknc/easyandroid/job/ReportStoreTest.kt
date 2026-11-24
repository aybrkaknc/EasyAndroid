package com.aybrkaknc.easyandroid.job

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReportStoreTest {
    private fun file(): File = File.createTempFile("reports", ".json").also { it.delete(); it.deleteOnExit() }

    private fun report(title: String, vararg kinds: ResultKind) =
        Report("2026-11-24T08:00:00+03:00", title, kinds.size, kinds.mapIndexed { i, k -> ReportItem("app$i", k.name, listOf("line")) })

    @Test
    fun keepsNewestFirstAndCountsResults() {
        val store = ReportStore(file())
        store.add(report("first", ResultKind.OK))
        store.add(report("second", ResultKind.OK, ResultKind.FAILED, ResultKind.WARNING))
        val all = store.read()
        assertEquals(listOf("second", "first"), all.map { it.title })
        assertEquals(1, all[0].failed)
        assertEquals(2, all[0].done)
    }

    @Test
    fun dropsTheOldestBeyondTheLimit() {
        val store = ReportStore(file(), limit = 3)
        (1..5).forEach { store.add(report("job$it", ResultKind.OK)) }
        assertEquals(listOf("job5", "job4", "job3"), store.read().map { it.title })
    }

    @Test
    fun damagedFileReadsAsEmptyAndRecovers() {
        val f = file()
        f.writeText("{ not json")
        val store = ReportStore(f)
        assertTrue(store.read().isEmpty())
        store.add(report("again", ResultKind.OK))
        assertEquals(listOf("again"), store.read().map { it.title })
    }

    @Test
    fun clearEmptiesTheLog() {
        val store = ReportStore(file())
        store.add(report("x", ResultKind.OK))
        store.clear()
        assertTrue(store.read().isEmpty())
    }
}
