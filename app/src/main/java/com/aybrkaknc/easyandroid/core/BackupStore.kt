package com.aybrkaknc.easyandroid.core

/** Device locations, replaced by tests that run without root. */
class Paths(
    val data: String = "/data/data",
    val deviceData: String = "/data/user_de/0",
    val externalData: String = "/data/media/0/Android/data",
    val obb: String = "/data/media/0/Android/obb",
)

class StoredBackup(val id: String, val manifest: Manifest)

class StoredFileSet(val id: String, val set: FileSet)

class StoredBackups(
    val backups: List<StoredBackup>,
    /** Folders that look like backups but have no valid manifest, e.g. after a crash. */
    val broken: List<String>,
    val systemSnapshots: List<String>,
    val fileSets: List<StoredFileSet> = emptyList(),
)

const val DEFAULT_ROOT = "/storage/emulated/0/EasyAndroid/Backups"

class BackupStore(private val shell: Root, root: String) {
    val root = Sh.checkPath(root)

    fun dir(id: String) = root + "/" + Sh.checkName(id)

    fun list(): StoredBackups {
        val script = """
            cd ${Sh.quote(root)} 2>/dev/null || exit 0
            for f in *; do
              case "${'$'}f" in
                system_*.json) echo "$MARK S ${'$'}f";;
                *) if [ -f "${'$'}f/$FILES_MANIFEST" ]; then echo "$MARK F ${'$'}f"; cat "${'$'}f/$FILES_MANIFEST"; echo
                   elif [ -d "${'$'}f" ]; then echo "$MARK D ${'$'}f"; cat "${'$'}f/$MANIFEST" 2>/dev/null; echo; fi;;
              esac
            done
        """.trimIndent()
        val out = shell.run(script).orThrow()
        val backups = ArrayList<StoredBackup>()
        val broken = ArrayList<String>()
        val system = ArrayList<String>()
        val fileSets = ArrayList<StoredFileSet>()
        for (block in "\n$out".split("\n$MARK ").drop(1)) {
            val name = block.substringBefore('\n').drop(2)
            if (runCatching { Sh.checkName(name) }.isFailure) continue
            if (block.startsWith("S ")) {
                system += name
                continue
            }
            if (block.startsWith("F ")) {
                val set = runCatching { FileSet.parse(block.substringAfter('\n', "")) }.getOrNull()
                if (set != null) fileSets += StoredFileSet(name, set) else if (FOLDER.containsMatchIn(name)) broken += name
                continue
            }
            val manifest = runCatching { Manifest.parse(block.substringAfter('\n', "")) }.getOrNull()
            if (manifest != null) backups += StoredBackup(name, manifest)
            else if (FOLDER.containsMatchIn(name)) broken += name
        }
        backups.sortByDescending { parseTimestamp(it.manifest.createdAt)?.toInstant()?.toEpochMilli() ?: 0 }
        fileSets.sortByDescending { parseTimestamp(it.set.createdAt)?.toInstant()?.toEpochMilli() ?: 0 }
        return StoredBackups(backups, broken, system.sortedDescending(), fileSets)
    }

    fun readFileSet(id: String): FileSet {
        val out = shell.run("cat ${Sh.quote(dir(id) + "/" + FILES_MANIFEST)}").orThrow(ErrorCode.BACKUP_INVALID)
        return FileSet.parse(out)
    }

    fun read(id: String): Manifest {
        val out = shell.run("cat ${Sh.quote(dir(id) + "/" + MANIFEST)}").orThrow(ErrorCode.BACKUP_INVALID)
        return Manifest.parse(out)
    }

    fun readText(name: String): String = shell.run("cat ${Sh.quote(dir(name))}").orThrow(ErrorCode.NOT_FOUND)

    fun writeText(path: String, text: String) {
        shell.open("cat > ${Sh.quote(path)}").use { stream ->
            stream.stdin.use { it.write(text.toByteArray()) }
            stream.finish().orThrow()
        }
    }

    fun ensureRoot() {
        shell.run("mkdir -p ${Sh.quote(root)}").orThrow()
    }

    /** Deletes one backup folder or snapshot file. Callers must have asked the user first. */
    fun delete(id: String) {
        shell.run("rm -rf ${Sh.quote(dir(id))}").orThrow()
    }

    /** Free bytes where backups are stored. */
    fun freeBytes(): Long {
        val out = shell.run("df -k ${Sh.quote(root)} | tail -n 1").orThrow()
        return parseDf(out) ?: throw AppException(ErrorCode.COMMAND_FAILED, out)
    }

    companion object {
        private const val MARK = "@@EA"
        private val FOLDER = Regex("_\\d{8}-\\d{6}(-\\d+)?$")

        /** `Filesystem 1K-blocks Used Available Use% Mounted`; counted around the percentage column. */
        fun parseDf(line: String): Long? {
            val columns = line.trim().split(Regex("\\s+"))
            val percent = columns.indexOfFirst { it.matches(Regex("\\d+%")) }
            return columns.getOrNull(percent - 1)?.toLongOrNull()?.times(1024)
        }
    }
}
