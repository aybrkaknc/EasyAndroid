package com.aybrkaknc.easyandroid.core

import com.aybrkaknc.easyandroid.Env
import java.io.File
import java.time.OffsetDateTime

/**
 * A plain text log in the app folder, for finding what went wrong. Failed root commands and every
 * job end are written. The file is kept small: when it grows past [LIMIT] it becomes the old file.
 */
object AppLog {
    private const val LIMIT = 300_000L
    private val lock = Any()

    private fun file(): File = File(Env.app.filesDir, "easyandroid.log")

    fun write(line: String) {
        val text = "${timestamp(OffsetDateTime.now())} ${line.take(600)}\n"
        synchronized(lock) {
            runCatching {
                val file = file()
                if (file.length() > LIMIT) file.renameTo(File(file.path + ".old"))
                file.appendText(text)
            }
        }
    }

    /** The last part of the log, for sharing. */
    fun tail(): String = synchronized(lock) {
        runCatching { file().takeIf { it.exists() }?.readText()?.takeLast(100_000) }.getOrNull().orEmpty()
    }
}
