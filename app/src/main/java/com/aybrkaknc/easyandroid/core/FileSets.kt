package com.aybrkaknc.easyandroid.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.time.OffsetDateTime

const val FILES_MANIFEST = "files.json"

@Serializable
enum class FileSetKind {
    @SerialName("media") MEDIA,
    @SerialName("folders") FOLDERS,
    @SerialName("wallpaper") WALLPAPER,
    @SerialName("calls") CALLS,
    @SerialName("messages") MESSAGES;

    /** Start of the backup folder name. */
    val prefix get() = name.lowercase()

    /** Wallpapers, call logs and messages live in fixed places; media and folders are chosen. */
    val fixed get() = this == WALLPAPER || this == CALLS || this == MESSAGES
}

/** One archive: the [names] inside [parent] on the device. */
@Serializable
data class FileSetItem(
    val parent: String,
    val names: List<String>,
    val stored: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class FileSet(
    val format: String,
    val version: Int,
    val kind: FileSetKind,
    val appVersion: String,
    val createdAt: String,
    val device: DeviceSnapshot,
    val items: List<FileSetItem>,
) {
    val totalBytes get() = items.sumOf { it.size }

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "easyandroid-files"

        /** Backups may come from anywhere and are extracted as root, so only known places are accepted. */
        fun parse(text: String): FileSet {
            val set = try {
                JSON.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw AppException(ErrorCode.BACKUP_INVALID, e.message.orEmpty())
            } catch (e: IllegalArgumentException) {
                throw AppException(ErrorCode.BACKUP_INVALID, e.message.orEmpty())
            }
            if (set.format != FORMAT || set.version != 1 || set.items.isEmpty()) {
                throw AppException(ErrorCode.BACKUP_INVALID, set.format)
            }
            for (item in set.items) {
                if (runCatching { Sh.checkRelative(item.stored) }.isFailure || !FileSets.allowed(set.kind, item.parent, item.names)) {
                    throw AppException(ErrorCode.BACKUP_INVALID, item.parent)
                }
            }
            return set
        }
    }
}

/** Where the fixed kinds live on the device, and what a file backup may touch. */
object FileSets {
    const val STORAGE = "/storage/emulated/0"

    /** [packageName] is stopped while the files are replaced. */
    class Place(val parent: String, val names: List<String>, val packageName: String?)

    private fun database(name: String) = listOf(name, "$name-wal", "$name-shm", "$name-journal")

    private val WALLPAPER = Place(
        "/data/system/users/0",
        listOf("wallpaper", "wallpaper_orig", "wallpaper_lock", "wallpaper_lock_orig", "wallpaper_info.xml"),
        null,
    )
    // The device-protected folder only has a shadow copy; the call log itself is in the credential-protected one.
    private val CALLS = Place(
        "/data/data/com.android.providers.contacts/databases", database("calllog.db"), "com.android.providers.contacts",
    )
    private val MESSAGES = Place(
        "/data/user_de/0/com.android.providers.telephony/databases", database("mmssms.db"), "com.android.providers.telephony",
    )

    /** Folders in the top of shared storage that a file backup never takes: apps and the backups themselves. */
    private val SKIPPED = setOf("Android", "EasyAndroid")

    fun place(kind: FileSetKind): Place? = when (kind) {
        FileSetKind.WALLPAPER -> WALLPAPER
        FileSetKind.CALLS -> CALLS
        FileSetKind.MESSAGES -> MESSAGES
        else -> null
    }

    fun isChoosable(name: String): Boolean =
        runCatching { Sh.checkName(name) }.isSuccess && name !in SKIPPED && !name.startsWith(".")

    fun allowed(kind: FileSetKind, parent: String, names: List<String>): Boolean {
        if (names.isEmpty()) return false
        val place = place(kind) ?: return parent == STORAGE && names.all(::isChoosable)
        return parent == place.parent && names.all { it in place.names }
    }
}

class Folder(val name: String, val bytes: Long)

/** Backs up and restores files as tar archives: shared storage folders, wallpapers, call logs and messages. */
class FileSetEngine(
    private val shell: Root,
    private val store: BackupStore,
    private val appVersion: String,
) {
    private fun q(text: String) = Sh.quote(text)

    private fun exists(path: String) = shell.run("[ -e ${q(path)} ]").ok

    /** The top-level folders of shared storage, with their sizes. Takes a while on a full phone. */
    fun folders(): List<Folder> {
        val script = """
            cd ${q(FileSets.STORAGE)} 2>/dev/null || exit 0
            for d in */; do
              d="${'$'}{d%/}"
              [ -d "${'$'}d" ] || continue
              printf '%s\t%s\n' "${'$'}(du -sk "${'$'}d" 2>/dev/null | cut -f1)" "${'$'}d"
            done
        """.trimIndent()
        // A backup folder somewhere else in storage must not end up inside its own backup.
        val own = store.root.removePrefix(FileSets.STORAGE + "/").substringBefore('/')
        return shell.run(script).out.lines().mapNotNull { line ->
            val name = line.substringAfter('\t', "")
            if (name.isEmpty() || !FileSets.isChoosable(name) || name == own) return@mapNotNull null
            Folder(name, (line.substringBefore('\t').trim().toLongOrNull() ?: 0) * 1024)
        }.sortedBy { it.name.lowercase() }
    }

    /** Which of the fixed [kind]'s files exist on this phone. */
    fun present(kind: FileSetKind): List<String> {
        val place = FileSets.place(kind) ?: return emptyList()
        return place.names.filter { exists("${place.parent}/$it") }
    }

    /** Folders for [FileSetKind.MEDIA] and [FileSetKind.FOLDERS] go in [chosen]; the fixed kinds ignore it. */
    fun backup(
        kind: FileSetKind,
        chosen: List<String>,
        device: DeviceSnapshot,
        task: Task,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): String {
        task.progress(Stage.PREPARE)
        val groups = ArrayList<Pair<String, List<String>>>()
        val place = FileSets.place(kind)
        if (place != null) {
            val found = present(kind)
            if (found.isEmpty()) throw AppException(ErrorCode.NOT_FOUND, place.parent)
            groups += place.parent to found
        } else {
            if (chosen.isEmpty()) throw AppException(ErrorCode.INVALID_INPUT, kind.prefix)
            for (name in chosen.distinct()) {
                if (!FileSets.isChoosable(name) || !exists("${FileSets.STORAGE}/$name")) throw AppException(ErrorCode.NOT_FOUND, name)
                groups += FileSets.STORAGE to listOf(name)
            }
        }
        val paths = groups.flatMap { (parent, names) -> names.map { "$parent/$it" } }
        val sizes = shell.run(paths.joinToString("; ") { "du -sk ${q(it)} 2>/dev/null" }).out
        val total = sizes.lines().sumOf { it.trim().substringBefore('\t').substringBefore(' ').toLongOrNull() ?: 0 } * 1024
        store.ensureRoot()
        if (store.freeBytes() - total < RESERVE) throw AppException(ErrorCode.NO_SPACE, total.toString())

        val id = folderName(kind.prefix, now)
        val dir = store.dir(id)
        if (exists(dir)) throw AppException(ErrorCode.INVALID_INPUT, id)
        try {
            shell.run("mkdir -p ${q(dir)}").orThrow()
            val transfer = Transfer(shell, task)
            var done = 0L
            val items = groups.mapIndexed { index, (parent, names) ->
                task.check()
                val stored = "$index.tar"
                val saved = transfer.store("tar -cf - -C ${q(parent)} ${names.joinToString(" ") { q(it) }}", "$dir/$stored", false) {
                    done += it
                    task.progress(Stage.FILES, done, maxOf(total, done))
                }
                if (saved.size == 0L) throw AppException(ErrorCode.COMMAND_FAILED, stored)
                FileSetItem(parent, names, stored, saved.size, saved.sha256)
            }
            task.check()
            // Written last, so that a folder without it is recognised as incomplete.
            val set = FileSet(FileSet.FORMAT, 1, kind, appVersion, timestamp(now), device, items)
            store.writeText("$dir/$FILES_MANIFEST", set.encode())
            return id
        } catch (e: Exception) {
            runCatching { store.delete(id) }
            throw e
        }
    }

    /** Everything is verified before anything on the device changes. */
    fun restore(id: String, task: Task) {
        val set = store.readFileSet(id)
        val dir = store.dir(id)
        val transfer = Transfer(shell, task)
        var done = 0L
        for (item in set.items) {
            transfer.verify("$dir/${item.stored}", item.size, item.sha256) {
                done += it
                task.progress(Stage.VERIFY, done, set.totalBytes)
            }
        }
        for (item in set.items) {
            task.check()
            checkArchive(transfer.untar("$dir/${item.stored}", false, "-tv"), item.names.toSet())
        }

        task.progress(Stage.FILES)
        val place = FileSets.place(set.kind)
        place?.packageName?.let { shell.run("am force-stop --user 0 $it") }
        try {
            for (item in set.items) {
                task.check()
                if (place == null) {
                    transfer.untar("$dir/${item.stored}", false, "-x -C ${q(item.parent)}")
                    continue
                }
                val owner = shell.run("stat -c %u:%g ${q(item.parent)}").orThrow().trim()
                if (!owner.matches(Regex("\\d+:\\d+"))) throw AppException(ErrorCode.COMMAND_FAILED, owner)
                // Leftovers of the old database (a journal, a write-ahead log) would corrupt the restored one.
                shell.run("rm -f " + place.names.joinToString(" ") { q("${item.parent}/$it") }).orThrow()
                transfer.untar("$dir/${item.stored}", false, "-x -C ${q(item.parent)}")
                val files = item.names.joinToString(" ") { q("${item.parent}/$it") }
                shell.run("chown -h $owner $files && restorecon -F $files").orThrow()
            }
        } finally {
            place?.packageName?.let { shell.run("am force-stop --user 0 $it") }
        }
    }

    private companion object {
        const val RESERVE = 512L * 1024 * 1024
    }
}
