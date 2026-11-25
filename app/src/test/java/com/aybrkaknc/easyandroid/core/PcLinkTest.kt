package com.aybrkaknc.easyandroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A phone whose shared folder is a few strings, and whose clock moves when the link waits. */
private class Mailbox : Root {
    var now = 1000L
    var beat: Long? = 995L
    val statuses = ArrayDeque<String>()
    var status = ""
    var request: String? = null
    var cancel: String? = null
    var job = ""
    var records = ""
    val calls = ArrayList<String>()

    override fun run(script: String): RootResult {
        calls += script
        return when {
            "cat /data/local/tmp/easyandroid/pc " in script -> RootResult(0, (beat?.let { "1 $it" } ?: "") + "\n@@\n$now")
            script == "date +%s" -> RootResult(0, "$now")
            script == "getprop ro.serialno" -> RootResult(0, "ABC123")
            "status.json 2>/dev/null" in script && script.startsWith("cat") -> RootResult(0, status)
            "pc-backups.json" in script -> RootResult(0, records)
            "job.json 2>/dev/null" in script -> RootResult(0, job)
            "printf %s" in script && "request.json.tmp" in script -> {
                request = Regex("printf %s ('.*?'|\\S+) >", RegexOption.DOT_MATCHES_ALL).find(script)!!.groupValues[1].trim('\'')
                RootResult(0, "")
            }
            "printf %s" in script && "/cancel.tmp" in script -> {
                cancel = Regex("printf %s (\\S+) >").find(script)!!.groupValues[1]
                RootResult(0, "")
            }
            else -> RootResult(0, "")
        }
    }

    override fun open(script: String): RootStream = throw UnsupportedOperationException(script)

    /** Each pause lets the PC say its next thing. */
    fun pause(ms: Long) {
        now += 1
        if (statuses.isNotEmpty()) status = statuses.removeFirst()
        if (beat != null) beat = now
    }
}

class PcLinkTest {
    private fun status(state: String, extra: String = "") =
        """{"id":"job-1","state":"$state"$extra}"""

    private fun request() = PcRequest(id = "job-1", created = 0, type = "app", packageName = "com.a.b", data = true)

    @Test
    fun theLinkIsUpWhileEasyAdbKeepsLooking() {
        val box = Mailbox()
        val link = PcLink(box, box::pause)
        assertTrue(link.connected())
        box.beat = 985
        assertFalse(link.connected())
        box.beat = null
        assertFalse(link.connected())
        assertNull(link.silence())
    }

    @Test
    fun aJobFollowsTheStatusUntilItIsDone() {
        val box = Mailbox()
        box.statuses += listOf(
            status("waiting"),
            status("running", ",\"stage\":\"data\",\"done\":5,\"total\":10"),
            status("done", ",\"notes\":[\"data_skipped\"]"),
        )
        val seen = ArrayList<Triple<Stage, Long, Long>>()
        val task = Task { stage, done, total -> seen += Triple(stage, done, total) }
        val result = PcLink(box, box::pause).send(request(), task)
        assertEquals(listOf("data_skipped"), result.notes)
        assertTrue(seen.contains(Triple(Stage.DATA, 5L, 10L)))
        assertTrue(Regex("\"package\": \"com.a.b\"").containsMatchIn(box.request!!))
        assertTrue(Regex("\"phone\": \"ABC123\"").containsMatchIn(box.request!!))
        // The shared folder is left clean.
        assertTrue(box.calls.last().startsWith("rm -f"))
    }

    @Test
    fun aJobThatNeedsTheUserWaitsAsLongAsThePcIsThere() {
        val box = Mailbox()
        box.statuses += List(100) { status("waiting") } + status("done")
        PcLink(box, box::pause).send(request(), Task())
    }

    @Test
    fun theLinkRefusesToStartWithoutAPc() {
        val box = Mailbox().apply { beat = null }
        failsWith(ErrorCode.PC_MISSING) { PcLink(box, box::pause).send(request(), Task()) }
        assertNull(box.request)
    }

    @Test
    fun aPcThatStopsLookingEndsTheWait() {
        val box = Mailbox()
        val link = PcLink(box) {
            box.now += 5
            // The PC never writes a heartbeat again.
        }
        failsWith(ErrorCode.PC_LOST) { link.send(request(), Task()) }
    }

    @Test
    fun aRefusalAndAFailureCarryTheirReason() {
        val rejected = Mailbox().apply { statuses += status("rejected", ",\"error\":\"no\"") }
        failsWith(ErrorCode.PC_REJECTED) { PcLink(rejected, rejected::pause).send(request(), Task()) }
        val failed = Mailbox().apply { statuses += status("failed", ",\"error\":\"AdbFailed: x\"") }
        try {
            PcLink(failed, failed::pause).send(request(), Task())
            throw AssertionError("expected a failure")
        } catch (e: AppException) {
            assertEquals(ErrorCode.PC_FAILED, e.code)
            assertEquals("AdbFailed: x", e.detail)
        }
    }

    @Test
    fun cancellingEndsTheWaitAndLeavesWordForThePc() {
        val box = Mailbox()
        box.statuses += status("running")
        val task = Task({ box.status.isNotEmpty() }) { _, _, _ -> }
        failsWith(ErrorCode.CANCELLED) { PcLink(box, box::pause).send(request(), task) }
        assertEquals("job-1", box.cancel)
        // Neither this job nor the next one takes the word back before the PC has read it.
        box.status = ""
        box.statuses += status("done")
        PcLink(box, box::pause).send(request(), Task())
        assertFalse(box.calls.any { it.startsWith("rm -f") && "/cancel" in it })
    }

    @Test
    fun waitingSaysWhatItIsFor() {
        val box = Mailbox()
        box.statuses += listOf(status("waiting", ",\"stage\":\"busy\""), status("waiting", ",\"stage\":\"approval\""), status("done"))
        val seen = ArrayList<Stage>()
        PcLink(box, box::pause).send(request(), Task { stage, _, _ -> seen += stage })
        assertEquals(listOf(Stage.TRANSFER, Stage.QUEUED, Stage.WAITING), seen)
    }

    @Test
    fun anUnansweredRequestIsNotARefusal() {
        val box = Mailbox().apply { statuses += status("rejected", ",\"error\":\"expired\"") }
        failsWith(ErrorCode.PC_EXPIRED) { PcLink(box, box::pause).send(request(), Task()) }
    }

    @Test
    fun aRequestThePcCouldNotReadEndsTheWait() {
        val box = Mailbox().apply { statuses += """{"id":"","state":"rejected","error":"unreadable"}""" }
        failsWith(ErrorCode.PC_REJECTED) { PcLink(box, box::pause).send(request(), Task()) }
    }

    @Test
    fun onlyOneRequestAtATime() {
        val box = Mailbox()
        val link = PcLink(box) {
            failsWith(ErrorCode.PC_BUSY) { PcLink(box, box::pause).send(request(), Task()) }
            box.status = status("done")
        }
        link.send(request(), Task())
    }

    @Test
    fun theRequestsOfOneTapShareAGroup() {
        val box = Mailbox().apply { statuses += status("done") }
        PcLink(box, box::pause).send(request().copy(batch = "g-1", index = 2, size = 3), Task())
        assertTrue(Regex("\"batch\": \"g-1\"").containsMatchIn(box.request!!))
        assertTrue(Regex("\"index\": 2").containsMatchIn(box.request!!))
        assertTrue(Regex("\"size\": 3").containsMatchIn(box.request!!))
    }

    @Test
    fun aJobSaysWhichPcItIsFrom() {
        val box = Mailbox()
        box.job = """{"id":"j-1","created":990,"type":"system","pc":"abc","pcName":"DESK"}"""
        assertEquals("abc" to "DESK", PcLink(box).pendingJob()!!.let { it.pc to it.pcName })
    }

    @Test
    fun anotherJobsStatusIsIgnored() {
        val box = Mailbox()
        box.statuses += listOf("""{"id":"old","state":"failed"}""", status("done"))
        PcLink(box, box::pause).send(request(), Task())
    }

    @Test
    fun readsWhatThePcHolds() {
        val box = Mailbox()
        box.records = """[{"package":"com.a","versionName":"1.0","createdAt":"2026-01-01T00:00:00+03:00"},{"package":"com.b"}]"""
        val records = PcLink(box).records()
        assertEquals(listOf("com.a", "com.b"), records.map { it.packageName })
        box.records = "not json"
        assertTrue(PcLink(box).records().isEmpty())
    }

    @Test
    fun aJobFromThePcMustBeFreshAndOfThisFormat() {
        val box = Mailbox()
        val link = PcLink(box)
        box.job = """{"id":"j-1","created":990,"type":"files","kinds":["messages"]}"""
        assertEquals(listOf("messages"), link.pendingJob()!!.kinds)
        box.job = """{"id":"j-1","created":100,"type":"files"}"""
        assertNull(link.pendingJob())
        box.job = """{"id":"a;b","created":990,"type":"files"}"""
        assertNull(link.pendingJob())
        box.job = """{"id":"j-1","created":990,"type":"files","format":"other"}"""
        assertNull(link.pendingJob())
        box.job = ""
        assertNull(link.pendingJob())
    }
}
