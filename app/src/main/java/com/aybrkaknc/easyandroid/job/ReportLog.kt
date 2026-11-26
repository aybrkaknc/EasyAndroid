package com.aybrkaknc.easyandroid.job

import android.content.Context
import com.aybrkaknc.easyandroid.core.AppLog
import com.aybrkaknc.easyandroid.core.JSON
import com.aybrkaknc.easyandroid.core.timestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.OffsetDateTime

@Serializable
data class ReportItem(val name: String, val kind: String, val lines: List<String>)

/** What a finished job did. Texts are kept in the language they were shown in. */
@Serializable
data class Report(val time: String, val title: String, val total: Int, val items: List<ReportItem>) {
    val failed get() = items.count { it.kind == ResultKind.FAILED.name }
    val done get() = items.size - failed
}

/** The last [limit] reports in one file, newest first. A damaged file reads as empty. */
class ReportStore(private val file: File, private val limit: Int = 50) {
    fun read(): List<Report> =
        runCatching { JSON.decodeFromString(ListSerializer(Report.serializer()), file.readText()) }.getOrDefault(emptyList())

    fun add(report: Report) {
        val text = JSON.encodeToString(ListSerializer(Report.serializer()), (listOf(report) + read()).take(limit))
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    fun clear() {
        file.delete()
    }
}

object ReportLog {
    private val lock = Any()

    private fun store(context: Context) = ReportStore(File(context.filesDir, "reports.json"))

    fun add(context: Context, job: JobState) {
        val report = Report(
            time = timestamp(OffsetDateTime.now()),
            title = context.getString(job.title),
            total = job.total,
            items = job.results.map { result -> ReportItem(result.name, result.kind.name, result.lines.map { it.resolve(context) }) },
        )
        AppLog.write("job ${report.title}: ${report.done} done, ${report.failed} failed of ${report.total}")
        report.items.filter { it.kind == ResultKind.FAILED.name }.forEach {
            AppLog.write("  failed ${it.name}: ${it.lines.joinToString(" ")}")
        }
        synchronized(lock) { runCatching { store(context).add(report) } }
    }

    fun read(context: Context): List<Report> = synchronized(lock) { store(context).read() }

    fun clear(context: Context) = synchronized(lock) { store(context).clear() }
}
