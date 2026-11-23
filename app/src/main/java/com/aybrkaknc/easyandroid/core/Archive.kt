package com.aybrkaknc.easyandroid.core

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** `.easybckp`: an uncompressed zip with a single backup folder, exchangeable with EasyADB. */
class Archive(private val shell: Root, private val store: BackupStore, private val task: Task) {
    fun export(id: String, output: OutputStream) {
        val manifest = store.read(id)
        val dir = store.dir(id)
        val entries = listOf(MANIFEST) + manifest.files.map { it.stored } + manifest.extras.map { it.stored }
        var done = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            for (stored in entries) {
                // Stored entries need their size and checksum up front.
                val crc = CRC32()
                var size = 0L
                stream("$dir/$stored") { buffer, n ->
                    crc.update(buffer, 0, n)
                    size += n
                }
                zip.putNextEntry(
                    ZipEntry("$id/$stored").apply {
                        method = ZipEntry.STORED
                        this.size = size
                        compressedSize = size
                        this.crc = crc.value
                    },
                )
                stream("$dir/$stored") { buffer, n ->
                    zip.write(buffer, 0, n)
                    done += n
                    task.progress(Stage.DATA, done, maxOf(done, manifest.totalBytes))
                }
                zip.closeEntry()
            }
        }
    }

    private fun stream(path: String, onChunk: (ByteArray, Int) -> Unit) {
        shell.open("cat ${Sh.quote(path)}").use { source ->
            source.stdin.close()
            val buffer = ByteArray(1 shl 17)
            while (true) {
                task.check()
                val n = source.stdout.read(buffer)
                if (n < 0) break
                onChunk(buffer, n)
            }
            source.finish().orThrow()
        }
    }

    /** Returns the new backup id. */
    fun import(input: InputStream): String {
        store.ensureRoot()
        var top: String? = null
        var id = ""
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = Sh.checkRelative(entry.name.trimEnd('/'))
                    val first = path.substringBefore('/')
                    if (top == null) {
                        id = freeId(first)
                        top = first
                    } else if (top != first) {
                        throw AppException(ErrorCode.BACKUP_INVALID, path)
                    }
                    if (entry.isDirectory || '/' !in path) continue
                    val dest = store.dir(id) + "/" + path.substringAfter('/')
                    write(zip, dest)
                }
            }
            if (top == null) throw AppException(ErrorCode.BACKUP_INVALID, "empty")
            store.read(id)
            return id
        } catch (e: Exception) {
            if (id.isNotEmpty()) runCatching { store.delete(id) }
            throw e
        }
    }

    private fun freeId(name: String): String {
        Sh.checkName(name)
        var id = name
        var n = 1
        while (shell.run("[ -e ${Sh.quote(store.dir(id))} ]").ok) id = "$name-${n++}"
        return id
    }

    private fun write(source: InputStream, dest: String) {
        val parent = dest.substringBeforeLast('/')
        shell.open("mkdir -p ${Sh.quote(parent)} && cat > ${Sh.quote(dest)}").use { sink ->
            val buffer = ByteArray(1 shl 17)
            sink.stdin.use { out ->
                while (true) {
                    task.check()
                    val n = source.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
            }
            sink.finish().orThrow()
        }
    }
}
