package com.aybrkaknc.easyandroid.job

import com.aybrkaknc.easyandroid.core.JSON
import com.aybrkaknc.easyandroid.core.PcJob
import com.aybrkaknc.easyandroid.core.PcProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiveTest {
    private fun at(state: String, index: Int) = PcProgress("a", state, index)

    @Test
    fun anItemArrivesWhenThePcIsPastIt() {
        assertEquals(ReceiveStep.WAITING, receiveStep(null, 0))
        assertEquals(ReceiveStep.RUNNING, receiveStep(at("running", 0), 0))
        assertEquals(ReceiveStep.WAITING, receiveStep(at("running", 0), 1))
        assertEquals(ReceiveStep.ARRIVED, receiveStep(at("running", 1), 0))
        assertEquals(ReceiveStep.ARRIVED, receiveStep(at("done", 1), 1))
    }

    @Test
    fun whatCameBeforeAStopStillCounts() {
        assertEquals(ReceiveStep.ARRIVED, receiveStep(at("cancelled", 2), 1))
        assertEquals(ReceiveStep.CANCELLED, receiveStep(at("cancelled", 2), 2))
        assertEquals(ReceiveStep.STOPPED, receiveStep(at("failed", 0), 0))
    }

    @Test
    fun readsWhatEasyAdbWrites() {
        val job = JSON.decodeFromString(
            PcJob.serializer(),
            """{"format":"easyandroid-job","version":1,"id":"pc-1-0","created":1,"type":"receive",""" +
                """"names":["a.zip"],"target":"/sdcard/Download","bytes":7,"pc":"x","pcName":"DESK"}""",
        )
        assertEquals(listOf("a.zip"), job.names)
        assertEquals(7L, job.bytes)
        val progress = JSON.decodeFromString(
            PcProgress.serializer(),
            """{"id":"pc-1-0","state":"running","index":1,"done":2,"total":3,"beat":4}""",
        )
        assertEquals(PcProgress("pc-1-0", "running", 1, 2, 3, 4), progress)
    }
}
