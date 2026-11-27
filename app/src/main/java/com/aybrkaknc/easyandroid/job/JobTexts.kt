package com.aybrkaknc.easyandroid.job

import android.content.Context
import com.aybrkaknc.easyandroid.R

/** What a job does, which decides the words it is shown with. */
enum class JobKind { BACKUP, RESTORE, EXPORT, IMPORT, DEBLOAT }

fun jobKind(title: Int): JobKind = when (title) {
    R.string.job_restore, R.string.job_system_restore -> JobKind.RESTORE
    R.string.job_export -> JobKind.EXPORT
    R.string.job_import -> JobKind.IMPORT
    R.string.job_debloat -> JobKind.DEBLOAT
    else -> JobKind.BACKUP
}

private fun runningTitle(kind: JobKind): Int = when (kind) {
    JobKind.BACKUP -> R.string.running_backup
    JobKind.RESTORE -> R.string.running_restore
    JobKind.EXPORT -> R.string.running_export
    JobKind.IMPORT -> R.string.running_import
    JobKind.DEBLOAT -> R.string.running_debloat
}

/** In the order of [Outcome]. */
private fun endTitles(kind: JobKind): List<Int> = when (kind) {
    JobKind.BACKUP -> listOf(R.string.end_backup_done, R.string.end_backup_warning, R.string.end_backup_incomplete, R.string.end_backup_cancelled)
    JobKind.RESTORE -> listOf(R.string.end_restore_done, R.string.end_restore_warning, R.string.end_restore_incomplete, R.string.end_restore_cancelled)
    JobKind.EXPORT -> listOf(R.string.end_export_done, R.string.end_export_warning, R.string.end_export_incomplete, R.string.end_export_cancelled)
    JobKind.IMPORT -> listOf(R.string.end_import_done, R.string.end_import_warning, R.string.end_import_incomplete, R.string.end_import_cancelled)
    JobKind.DEBLOAT -> listOf(R.string.end_debloat_done, R.string.end_debloat_warning, R.string.end_debloat_incomplete, R.string.end_debloat_cancelled)
}

/** The first heading of the job: what it does while it runs, how it ended after that. */
fun heading(state: JobState): Int {
    val kind = jobKind(state.title)
    return if (state.finished) endTitles(kind)[outcomeOf(state.cancelled, state.results).ordinal] else runningTitle(kind)
}

/** The second heading after the end: the name of the only item, or how many of them made it. */
fun summary(context: Context, state: JobState): String {
    if (state.total == 1) return state.results.firstOrNull()?.name ?: state.item
    val done = state.results.count { it.kind != ResultKind.FAILED }
    val apps = state.info.packageName != null
    val id = when (jobKind(state.title)) {
        JobKind.BACKUP -> if (apps) R.string.summary_apps_backed_up else R.string.summary_items_done
        JobKind.RESTORE -> if (apps) R.string.summary_apps_restored else R.string.summary_items_done
        else -> R.string.summary_items_done
    }
    return context.getString(id, done, state.total)
}
