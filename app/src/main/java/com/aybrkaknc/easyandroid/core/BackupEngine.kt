package com.aybrkaknc.easyandroid.core

import java.time.OffsetDateTime

data class BackupOptions(
    val obb: Boolean = true,
    val data: Boolean = true,
    val externalData: Boolean = false,
    val compress: Boolean = false,
)

/** Something that did not work but did not stop the job either. */
enum class Note { DATA_FAILED, DEVICE_DATA_FAILED, EXTERNAL_DATA_FAILED, OBB_FAILED, PERMISSIONS_PARTIAL, APP_OPS_FAILED }

class NoteDetail(val note: Note, val detail: String)

class BackupOutcome(val id: String, val manifest: Manifest, val notes: List<NoteDetail>)

class BackupEngine(
    private val shell: Root,
    private val store: BackupStore,
    private val appVersion: String,
    private val paths: Paths = Paths(),
) {
    fun snapshot(): DeviceSnapshot {
        val out = shell.run(
            "getprop ro.product.model; getprop ro.serialno; getprop ro.build.version.release; getprop ro.build.version.sdk",
        ).out.lines()
        fun line(index: Int) = out.getOrNull(index)?.trim().orEmpty()
        return DeviceSnapshot(line(0), line(1), line(2), line(3).toIntOrNull() ?: 0)
    }

    private fun q(text: String) = Sh.quote(text)

    private fun exists(path: String) = shell.run("[ -e ${q(path)} ]").ok

    /** Upper bound of what a backup will take, before compression. */
    fun estimate(packageName: String, options: BackupOptions): Long {
        Sh.checkPackage(packageName)
        val apks = Parsers.pmPath(shell.run("pm path --user 0 $packageName").out)
        val sources = apks.toMutableList()
        if (options.obb) sources += "${paths.obb}/$packageName"
        if (options.data) sources += listOf("${paths.data}/$packageName/", "${paths.deviceData}/$packageName/")
        if (options.externalData) sources += "${paths.externalData}/$packageName"
        val out = shell.run(sources.joinToString("; ") { "du -sk ${q(it)} 2>/dev/null" }).out
        return out.lines().sumOf { it.trim().substringBefore('\t').substringBefore(' ').toLongOrNull() ?: 0 } * 1024
    }

    fun backup(
        packageName: String,
        label: String?,
        options: BackupOptions,
        device: DeviceSnapshot,
        task: Task,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): BackupOutcome {
        Sh.checkPackage(packageName)
        task.progress(Stage.PREPARE)
        val apks = Parsers.pmPath(shell.run("pm path --user 0 $packageName").out)
        if (apks.isEmpty()) throw AppException(ErrorCode.NOT_FOUND, packageName)
        val total = estimate(packageName, options)
        store.ensureRoot()
        // Keep some room so that a full storage never breaks the phone.
        if (store.freeBytes() - total < RESERVE) throw AppException(ErrorCode.NO_SPACE, total.toString())

        val dump = shell.run("dumpsys package $packageName").out
        val appOps = Parsers.appOps(shell.run("cmd appops get --user 0 $packageName").out)
        val exempt = packageName in Parsers.batteryExempt(shell.run("dumpsys deviceidle whitelist").out)
        val id = folderName(packageName, now)
        val dir = store.dir(id)
        if (exists(dir)) throw AppException(ErrorCode.INVALID_INPUT, id)
        if (options.data || options.externalData) shell.stopUnlessSystem(packageName)

        try {
            shell.run("mkdir -p ${q("$dir/apk")}").orThrow()
            val notes = ArrayList<NoteDetail>()
            var done = 0L
            val transfer = Transfer(shell, task)
            fun save(stage: Stage, script: String, stored: String, compress: Boolean = false) =
                transfer.store(script, "$dir/$stored", compress) {
                    done += it
                    task.progress(stage, done, maxOf(total, done))
                }

            val files = ArrayList<BackupFile>()
            for (apk in apks) {
                val name = Sh.checkName(apk.substringAfterLast('/'))
                val saved = save(Stage.APK, "cat ${q(apk)}", "apk/$name")
                if (saved.size == 0L) throw AppException(ErrorCode.COMMAND_FAILED, apk)
                files += BackupFile(FileKind.APK, "apk/$name", name, saved.size, saved.sha256)
            }

            if (options.obb) files += saveObb(packageName, dir, notes) { script, stored -> save(Stage.OBB, script, stored) }

            /** Archives `<parent>/<package>`; a failure drops only this archive. */
            fun archive(parent: String, name: String, note: Note): Pair<String, Stored>? {
                if (!exists("$parent/$packageName/")) {
                    // Every installed app has a private folder. Not seeing it must not look like success.
                    if (note == Note.DATA_FAILED) notes += NoteDetail(note, "$parent/$packageName")
                    return null
                }
                val stored = if (options.compress) "$name.tar.zst" else "$name.tar"
                return try {
                    val sockets = shell.run("find ${q("$parent/$packageName")} -type s 2>/dev/null").out
                    val step = if (note == Note.EXTERNAL_DATA_FAILED) Stage.EXTERNAL else Stage.DATA
                    val saved = save(step, tarScript(parent, packageName, sockets), stored, options.compress)
                    if (saved.size == 0L) throw AppException(ErrorCode.COMMAND_FAILED, stored)
                    stored to saved
                } catch (e: Exception) {
                    if (e is AppException && e.code == ErrorCode.CANCELLED) throw e
                    shell.run("rm -f ${q("$dir/$stored")}")
                    notes += NoteDetail(note, e.message.orEmpty())
                    null
                }
            }

            val extras = ArrayList<ExtraFile>()
            if (options.data) {
                archive(paths.data, "data", Note.DATA_FAILED)?.let { (stored, saved) ->
                    files += BackupFile(FileKind.DATA, stored, stored, saved.size, saved.sha256)
                }
                archive(paths.deviceData, "data_de", Note.DEVICE_DATA_FAILED)?.let { (stored, saved) ->
                    extras += ExtraFile(ExtraKind.DEVICE_DATA, stored, saved.size, saved.sha256)
                }
            }
            if (options.externalData) {
                archive(paths.externalData, "data_ext", Note.EXTERNAL_DATA_FAILED)?.let { (stored, saved) ->
                    extras += ExtraFile(ExtraKind.EXTERNAL_DATA, stored, saved.size, saved.sha256)
                }
            }

            task.check()
            val compressed = files.any { it.stored.endsWith(".zst") } || extras.any { it.stored.endsWith(".zst") }
            val manifest = Manifest(
                formatVersion = if (compressed) FORMAT_ZSTD else FORMAT_PLAIN,
                appVersion = appVersion,
                packageName = packageName,
                label = label,
                versionName = Parsers.versionName(dump),
                createdAt = timestamp(now),
                device = device,
                files = files,
                permissions = Parsers.runtimePermissions(dump),
                extras = extras,
                appOps = appOps,
                batteryExempt = exempt,
            )
            // The manifest comes last: a folder without one is recognised as incomplete.
            store.writeText("$dir/$MANIFEST", manifest.encode())
            return BackupOutcome(id, manifest, notes)
        } catch (e: Exception) {
            runCatching { store.delete(id) }
            throw e
        }
    }

    private fun saveObb(
        packageName: String,
        dir: String,
        notes: MutableList<NoteDetail>,
        save: (String, String) -> Stored,
    ): List<BackupFile> {
        val found = shell.run("find ${q("${paths.obb}/$packageName")} -type f 2>/dev/null").out
            .lines().filter { it.startsWith("/") }
        if (found.isEmpty()) return emptyList()
        shell.run("mkdir -p ${q("$dir/obb")}").orThrow()
        return found.mapNotNull { path ->
            try {
                val name = Sh.checkName(path.substringAfterLast('/'))
                val saved = save("cat ${q(path)}", "obb/$name")
                BackupFile(FileKind.OBB, "obb/$name", name, saved.size, saved.sha256)
            } catch (e: AppException) {
                if (e.code == ErrorCode.CANCELLED) throw e
                notes += NoteDetail(Note.OBB_FAILED, e.detail)
                null
            }
        }
    }

    private companion object {
        const val RESERVE = 512L * 1024 * 1024
    }
}

/** Stops an app so that its data holds still. A system app is left running: Android would start it again at once. */
internal fun Root.stopUnlessSystem(packageName: String) {
    val system = run("pm list packages -s --user 0 $packageName").out.lines()
        .any { it.trim() == "package:$packageName" }
    if (!system) run("am force-stop --user 0 $packageName")
}

/**
 * `tar` script for one data folder. The caches are rebuilt by the app and only add size.
 * [sockets] are full paths, one per line: `tar` fails on them and the app makes them again.
 */
internal fun tarScript(parent: String, packageName: String, sockets: String): String {
    val caches = listOf("cache", "code_cache", "lib").map { "$packageName/$it" }
    val found = sockets.lines().filter { it.startsWith("$parent/") }.map { Sh.quote(it.removePrefix("$parent/")) }
    val excludes = (caches + found).joinToString(" ") { "--exclude $it" }
    return "tar -cf - -C ${Sh.quote(parent)} $excludes $packageName"
}
