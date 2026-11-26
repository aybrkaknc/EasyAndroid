package com.aybrkaknc.easyandroid.core

import com.topjohnwu.superuser.Shell
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class RootResult(val code: Int, val out: String, val err: String = "") {
    val ok get() = code == 0

    fun orThrow(code: ErrorCode = ErrorCode.COMMAND_FAILED): String =
        if (ok) out else throw AppException(code, (err.ifBlank { out }).trim())
}

/** A running root command whose stdin and stdout are streamed by the app. */
interface RootStream : AutoCloseable {
    val stdin: OutputStream
    val stdout: InputStream

    /** Waits for the command. Close [stdin] and drain [stdout] first. */
    fun finish(): RootResult
}

/** The only way the engines touch the device, so they can be tested without one. */
interface Root {
    fun run(script: String): RootResult
    fun open(script: String): RootStream
}

/** A process that already runs [RootStream]'s command. Shared with the adb based test runner. */
class ProcessStream(private val process: Process) : RootStream {
    private val errors = StringBuilder()
    private val drain = thread(isDaemon = true) {
        runCatching { process.errorStream.bufferedReader().forEachLine { errors.appendLine(it) } }
    }
    override val stdin: OutputStream = process.outputStream
    override val stdout: InputStream = process.inputStream

    override fun finish(): RootResult {
        val code = process.waitFor()
        drain.join()
        return RootResult(code, "", errors.toString())
    }

    override fun close() {
        process.destroy()
    }
}

/** Magisk, KernelSU, APatch and their forks: one shared libsu shell plus `su` processes for streams. */
object SuRoot : Root {
    fun available(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)

    /** Drops a cached non-root shell so that root is requested again. */
    fun retry(): Boolean {
        Shell.getCachedShell()?.let { if (!it.isRoot) it.close() }
        return available()
    }

    // The global mount namespace is needed to see /storage and /data/media like the system does.
    private val su: List<String> by lazy {
        val master = listOf("su", "--mount-master", "-c")
        if (runCatching { start(master, "id -u").finishText() }.getOrNull() == "0") master else listOf("su", "-c")
    }

    private fun start(prefix: List<String>, script: String) = ProcessStream(ProcessBuilder(prefix + script).start())

    private fun ProcessStream.finishText(): String? {
        stdin.close()
        val text = stdout.bufferedReader().readText().trim()
        return if (finish().ok) text else null
    }

    override fun run(script: String): RootResult {
        if (!available()) throw AppException(ErrorCode.NO_ROOT)
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val started = System.currentTimeMillis()
        // The subshell keeps `exit` from closing the shared shell, /dev/null keeps commands off its stdin.
        val result = Shell.cmd("(\n$script\n) </dev/null").to(out, err).exec()
        val root = RootResult(result.code, out.joinToString("\n"), err.joinToString("\n"))
        // Only failures are written; logging every command would make the file huge.
        if (root.code != 0) {
            val took = System.currentTimeMillis() - started
            val text = script.trim().replace("\n", " ").take(200)
            AppLog.write("root exit ${root.code} $took ms $text | ${root.err.trim().replace("\n", " ").take(300)}")
        }
        return root
    }

    override fun open(script: String): RootStream {
        if (!available()) throw AppException(ErrorCode.NO_ROOT)
        return start(su, script)
    }
}
