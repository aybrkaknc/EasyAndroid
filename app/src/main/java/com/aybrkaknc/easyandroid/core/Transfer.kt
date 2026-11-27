package com.aybrkaknc.easyandroid.core

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.concurrent.thread

enum class Stage { PREPARE, APK, OBB, DATA, EXTERNAL, FILES, VERIFY, INSTALL, PERMISSIONS, DEBLOAT, SETTINGS, TRANSFER, WAITING, QUEUED }

/** Progress and cancellation of one job. */
class Task(
    private val isCancelled: () -> Boolean = { false },
    private val onStep: (String) -> Unit = {},
    private val onSeen: (Set<Stage>) -> Unit = {},
    private val onProgress: (Stage, Long, Long) -> Unit = { _, _, _ -> },
) {
    fun cancelled() = isCancelled()

    /** The item the PC works on now, for example a package name. Empty when none. */
    fun step(item: String) = onStep(item)

    fun check() {
        if (cancelled()) throw AppException(ErrorCode.CANCELLED)
    }

    fun progress(stage: Stage, done: Long = 0, total: Long = 0) = onProgress(stage, done, total)

    /** The parts that the PC came across in the item it works on. */
    fun seen(stages: Set<Stage>) = onSeen(stages)
}

class Stored(val size: Long, val sha256: String)

/** Moves bytes between root commands, hashing and counting what is stored. */
class Transfer(private val shell: Root, private val task: Task) {
    private class Meter(private val sink: OutputStream?) : OutputStream() {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        var count = 0L

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            sink?.write(b, off, len)
            digest.update(b, off, len)
            count += len
        }

        override fun flush() = sink?.flush() ?: Unit
        override fun close() = sink?.close() ?: Unit

        fun stored() = Stored(count, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun copy(from: InputStream, to: OutputStream, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(1 shl 17)
        while (true) {
            task.check()
            val n = from.read(buffer)
            if (n < 0) return
            to.write(buffer, 0, n)
            onBytes(n.toLong())
        }
    }

    /** Stores the stdout of [script] in [dest]. [onBytes] counts uncompressed bytes. */
    fun store(script: String, dest: String, compress: Boolean, onBytes: (Long) -> Unit): Stored {
        val stored = if (compress) storeZstd(script, dest, onBytes) else storePlain(script, dest, onBytes)
        val written = shell.run("stat -c %s ${Sh.quote(dest)}").orThrow().trim().toLongOrNull()
        if (written != stored.size) throw AppException(ErrorCode.INTEGRITY, "$dest: $written != ${stored.size}")
        return stored
    }

    /** `tee` writes on the root side, so one root process is enough. */
    private fun storePlain(script: String, dest: String, onBytes: (Long) -> Unit): Stored =
        shell.open("set -o pipefail; $script | tee ${Sh.quote(dest)}").use { source ->
            source.stdin.close()
            val meter = Meter(null)
            copy(source.stdout, meter, onBytes)
            checkSource(source.finish())
            meter.stored()
        }

    private fun storeZstd(script: String, dest: String, onBytes: (Long) -> Unit): Stored =
        shell.open(script).use { source ->
            source.stdin.close()
            shell.open("cat > ${Sh.quote(dest)}").use { sink ->
                val meter = Meter(sink.stdin)
                ZstdOutputStream(meter, 3).use { copy(source.stdout, it, onBytes) }
                sink.finish().orThrow()
                checkSource(source.finish())
                meter.stored()
            }
        }

    private fun checkSource(result: RootResult) {
        if (result.ok) return
        val lines = result.err.lines().filter { it.isNotBlank() }
        // Sockets cannot be archived and files may vanish while tar runs; neither spoils the archive.
        val harmless = lines.isNotEmpty() && lines.all { line ->
            line.startsWith("tar:") && HARMLESS.any { it in line }
        }
        if (!harmless) result.orThrow()
    }

    fun verify(path: String, size: Long, sha256: String, onBytes: (Long) -> Unit) {
        val actual = shell.open("cat ${Sh.quote(path)}").use { source ->
            source.stdin.close()
            val meter = Meter(null)
            copy(source.stdout, meter, onBytes)
            if (!source.finish().ok) throw AppException(ErrorCode.INTEGRITY, path)
            meter.stored()
        }
        if (actual.size != size || !actual.sha256.equals(sha256, ignoreCase = true)) {
            throw AppException(ErrorCode.INTEGRITY, path)
        }
    }

    /** Feeds an archive to `tar <args>` and returns its output. */
    fun untar(archive: String, compressed: Boolean, args: String): String {
        if (!compressed) return shell.run("tar -f ${Sh.quote(archive)} $args").orThrow()
        return shell.open("cat ${Sh.quote(archive)}").use { source ->
            source.stdin.close()
            shell.open("tar -f - $args").use { sink ->
                var output = ""
                val reader = thread { output = runCatching { sink.stdout.bufferedReader().readText() }.getOrDefault("") }
                // tar may stop reading at the end of the archive; its exit code decides.
                val fed = runCatching {
                    ZstdInputStream(source.stdout).use { zstd -> sink.stdin.use { zstd.copyTo(it) } }
                }
                val result = sink.finish()
                reader.join()
                result.orThrow()
                fed.getOrThrow()
                output
            }
        }
    }

    private companion object {
        val HARMLESS = listOf("unknown file type", "No such file or directory", "removing leading")
    }
}

/** Mode, owner, size, date and then the name. */
private val LISTING = Regex("\\S+\\s+\\S+\\s+\\d+\\s+\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(?::\\S+)?(?: [+-]\\d{4})? (.+)")

/**
 * Backups may come from anywhere and are extracted as root, so every entry has to stay
 * inside the package folder. Takes the output of `tar -tv`.
 */
fun checkArchive(listing: String, packageName: String) = checkArchive(listing, setOf(packageName))

/** The same check for an archive of several top-level [roots]. */
fun checkArchive(listing: String, roots: Set<String>) {
    fun reject(line: String): Nothing = throw AppException(ErrorCode.BACKUP_INVALID, line)
    fun inside(path: String) = roots.any { path == it || path.startsWith("$it/") }
    val lines = listing.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) reject("empty archive")
    val names = ArrayList<String>()
    val links = HashSet<String>()
    for (line in lines) {
        val rest = LISTING.matchEntire(line)?.groupValues?.get(1) ?: reject(line)
        val name = when (line[0]) {
            'l' -> rest.substringBefore(" -> ").also { links += it }
            'h' -> {
                val target = rest.substringAfter(" link to ", "").ifEmpty { rest.substringAfter(" -> ", "") }
                if (roots.none { target.startsWith("$it/") } || ".." in target.split('/')) reject(line)
                rest.substringBefore(" link to ").substringBefore(" -> ")
            }
            '-', 'd', 'p', 's' -> rest
            else -> reject(line)
        }
        if (!inside(name)) reject(line)
        if (".." in name.split('/')) reject(line)
        names += name
    }
    // Writing through an archived symlink would leave the folder.
    for (name in names) {
        var parent = name.trimEnd('/').substringBeforeLast('/', "")
        while (parent.isNotEmpty()) {
            if (parent in links) reject(name)
            parent = parent.substringBeforeLast('/', "")
        }
    }
    roots.firstOrNull { it in links }?.let { reject(it) }
}
