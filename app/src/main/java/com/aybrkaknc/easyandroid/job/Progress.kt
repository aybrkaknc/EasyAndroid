package com.aybrkaknc.easyandroid.job

import com.aybrkaknc.easyandroid.core.Stage

/** What is known about an item of a job before it runs, for the page that shows it. */
data class ItemInfo(
    /** Where a backup goes. */
    val detail: UiText? = null,
    /** When the backup that is restored was made. */
    val createdAt: String? = null,
    /** The package whose icon stands for the item. */
    val packageName: String? = null,
    /** The parts an app backup includes, in the order they are copied. Empty for other jobs. */
    val parts: List<Stage> = emptyList(),
    /** True when the PC does the work, so only it knows which parts it came across. */
    val remote: Boolean = false,
    /** The folder that incoming files go to. */
    val folder: String? = null,
)

enum class StepState { DONE, RUNNING, WAITING, ABSENT }

/** How each of [parts] stands while [current] runs. [seen] is null when that is not known. */
fun stepStates(parts: List<Stage>, current: Stage, seen: Set<Stage>?): List<StepState> {
    val at = parts.indexOf(current)
    return parts.mapIndexed { index, part ->
        when {
            at < 0 || index > at -> StepState.WAITING
            index == at -> StepState.RUNNING
            // A part that passed without moving anything is one the app does not have.
            seen != null && part !in seen -> StepState.ABSENT
            else -> StepState.DONE
        }
    }
}

/** How fast the bytes of the current item move. */
data class Lane(
    val done: Long,
    val total: Long,
    /** Bytes per second over the last half second or more. */
    val rate: Double,
    private val sampleAt: Long,
    private val sampleDone: Long,
) {
    /** Seconds left for the item, or null while its speed is not known. */
    val secondsLeft: Long?
        get() = if (rate <= 0 || total <= done) null else kotlin.math.ceil((total - done) / rate).toLong()

    fun next(done: Long, total: Long, now: Long): Lane {
        // Counting started again, so the last speed stands until a new one can be measured.
        if (done < this.done) return Lane(done, total, rate, now, done)
        val ms = now - sampleAt
        if (ms < SAMPLE_MS) return copy(done = done, total = total)
        // Progress comes in steps: no new bytes for a moment does not mean that nothing moves.
        if (done == sampleDone && ms < STALL_MS) return copy(total = total)
        return Lane(done, total, (done - sampleDone) * 1000.0 / ms, now, done)
    }

    /** For the next item of a job: its bytes are counted from the start, the speed stays. */
    fun restart(): Lane = copy(done = Long.MAX_VALUE, total = 0)

    private companion object {
        const val SAMPLE_MS = 500L
        const val STALL_MS = 3_000L
    }
}

/** The same rule that EasyADB uses for its progress window. */
fun nextLane(previous: Lane?, done: Long, total: Long, now: Long): Lane =
    previous?.next(done, total, now) ?: Lane(done, total, 0.0, now, done)

enum class Outcome { DONE, WARNING, INCOMPLETE, CANCELLED }

fun outcomeOf(cancelled: Boolean, results: List<JobResult>): Outcome = when {
    cancelled -> Outcome.CANCELLED
    results.any { it.kind == ResultKind.FAILED } -> Outcome.INCOMPLETE
    results.any { it.kind == ResultKind.WARNING } -> Outcome.WARNING
    else -> Outcome.DONE
}

class Arranged<T>(val problems: List<T>, val notTried: List<T>, val fine: List<T>)

/** Problems first, then what was never tried as one group, then what went well. */
fun <T> arrange(rows: List<T>, kind: (T) -> ResultKind, notTried: (T) -> Boolean): Arranged<T> {
    val (skipped, tried) = rows.partition(notTried)
    val (fine, problems) = tried.partition { kind(it) == ResultKind.OK }
    return Arranged(problems.sortedByDescending { kind(it) }, skipped, fine)
}
