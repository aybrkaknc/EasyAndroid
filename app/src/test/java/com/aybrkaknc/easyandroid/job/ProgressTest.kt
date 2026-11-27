package com.aybrkaknc.easyandroid.job

import com.aybrkaknc.easyandroid.core.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    private val parts = listOf(Stage.APK, Stage.OBB, Stage.DATA, Stage.EXTERNAL)

    private fun result(kind: ResultKind) = JobResult("a", kind, emptyList())

    @Test
    fun aPartThatPassedWithoutBeingSeenIsAbsent() {
        val states = stepStates(parts, Stage.DATA, setOf(Stage.APK, Stage.DATA))
        assertEquals(listOf(StepState.DONE, StepState.ABSENT, StepState.RUNNING, StepState.WAITING), states)
    }

    @Test
    fun nothingIsCalledAbsentWhenWhatWasSeenIsNotKnown() {
        val states = stepStates(parts, Stage.DATA, null)
        assertEquals(listOf(StepState.DONE, StepState.DONE, StepState.RUNNING, StepState.WAITING), states)
    }

    @Test
    fun beforeTheFirstPartEverythingWaits() {
        assertEquals(List(4) { StepState.WAITING }, stepStates(parts, Stage.WAITING, emptySet()))
    }

    @Test
    fun theSpeedComesFromHalfASecondOrMore() {
        val first = nextLane(null, 0, 1000, 0)
        val soon = nextLane(first, 100, 1000, 100)
        assertEquals(0.0, soon.rate, 0.0)
        val later = nextLane(soon, 500, 1000, 1000)
        assertEquals(500.0, later.rate, 0.0)
        assertEquals(1L, later.secondsLeft)
        assertNull(first.secondsLeft)
    }

    @Test
    fun aMomentWithoutNewBytesKeepsTheSpeed() {
        val moving = nextLane(nextLane(null, 0, 1000, 0), 500, 1000, 1000)
        assertEquals(500.0, nextLane(moving, 500, 1000, 1600).rate, 0.0)
        assertEquals(0.0, nextLane(moving, 500, 1000, 5000).rate, 0.0)
    }

    @Test
    fun theSpeedStaysWhenCountingStartsAgain() {
        val moving = nextLane(nextLane(null, 0, 1000, 0), 500, 1000, 1000)
        assertEquals(500.0, nextLane(moving, 10, 1000, 2000).rate, 0.0)
        val next = nextLane(moving.restart(), 900, 2000, 1200)
        assertEquals(500.0, next.rate, 0.0)
        assertEquals(1000.0, nextLane(next, 1900, 2000, 2200).rate, 0.0)
    }

    @Test
    fun theOutcomeSaysTheWorstThatHappened() {
        assertEquals(Outcome.DONE, outcomeOf(false, listOf(result(ResultKind.OK))))
        assertEquals(Outcome.WARNING, outcomeOf(false, listOf(result(ResultKind.OK), result(ResultKind.WARNING))))
        assertEquals(Outcome.INCOMPLETE, outcomeOf(false, listOf(result(ResultKind.WARNING), result(ResultKind.FAILED))))
        assertEquals(Outcome.CANCELLED, outcomeOf(true, listOf(result(ResultKind.FAILED))))
    }

    @Test
    fun problemsComeFirstAndWhatWasNotTriedIsGathered() {
        val rows = listOf(
            JobResult("ok", ResultKind.OK, emptyList()),
            JobResult("skipped", ResultKind.FAILED, emptyList(), notTried = true),
            JobResult("warned", ResultKind.WARNING, emptyList()),
            JobResult("broken", ResultKind.FAILED, emptyList()),
        )
        val arranged = arrange(rows, { it.kind }, { it.notTried })
        assertEquals(listOf("broken", "warned"), arranged.problems.map { it.name })
        assertEquals(listOf("skipped"), arranged.notTried.map { it.name })
        assertEquals(listOf("ok"), arranged.fine.map { it.name })
    }
}
