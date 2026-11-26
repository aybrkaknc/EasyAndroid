package com.aybrkaknc.easyandroid.core

data class RestoreOptions(
    val app: Boolean = true,
    val obb: Boolean = true,
    val data: Boolean = true,
    val permissions: Boolean = true,
)

class RestoreOutcome(val permissionsGranted: Int, val permissionsWanted: Int, val notes: List<NoteDetail>)

/** Everything is verified before anything on the device changes. */
class RestoreEngine(
    private val shell: Root,
    private val store: BackupStore,
    private val paths: Paths = Paths(),
) {
    private fun q(text: String) = Sh.quote(text)

    fun restore(id: String, options: RestoreOptions, sdk: Int, task: Task): RestoreOutcome {
        val manifest = store.read(id)
        val packageName = manifest.packageName
        val dir = store.dir(id)
        val transfer = Transfer(shell, task)

        val apks = manifest.files.filter { it.kind == FileKind.APK && options.app }
        val obbs = manifest.files.filter { it.kind == FileKind.OBB && options.obb }
        val data = manifest.files.firstOrNull { it.kind == FileKind.DATA && options.data }
        val extras = manifest.extras.filter { options.data }
        if (options.app && apks.isEmpty()) throw AppException(ErrorCode.BACKUP_INVALID, id)

        val checks = (apks + obbs + listOfNotNull(data)).map { Triple(it.stored, it.size, it.sha256) } +
            extras.map { Triple(it.stored, it.size, it.sha256) }
        val total = checks.sumOf { it.second }
        var done = 0L
        for ((stored, size, sha) in checks) {
            transfer.verify("$dir/$stored", size, sha) {
                done += it
                task.progress(Stage.VERIFY, done, total)
            }
        }
        val archives = listOfNotNull(data?.stored) + extras.map { it.stored }
        for (stored in archives) {
            task.check()
            checkArchive(transfer.untar("$dir/$stored", stored.endsWith(".zst"), "-tv"), packageName)
        }

        task.check()
        if (options.app) {
            task.progress(Stage.INSTALL)
            install(apks, dir, sdk)
        } else if (Parsers.pmPath(shell.run("pm path --user 0 $packageName").out).isEmpty()) {
            throw AppException(ErrorCode.NOT_FOUND, packageName)
        }

        val notes = ArrayList<NoteDetail>()
        fun attempt(note: Note, block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                notes += NoteDetail(note, (e as? AppException)?.detail ?: e.message.orEmpty())
            }
        }

        if (archives.isNotEmpty() || obbs.isNotEmpty()) shell.stopUnlessSystem(packageName)
        if (obbs.isNotEmpty()) {
            task.progress(Stage.OBB)
            attempt(Note.OBB_FAILED) { restoreObb(obbs, dir, packageName, sdk) }
        }
        if (archives.isNotEmpty()) task.progress(Stage.DATA)
        data?.let {
            attempt(Note.DATA_FAILED) { restoreInternal(transfer, "$dir/${it.stored}", paths.data, packageName) }
        }
        for (extra in extras) {
            val archive = "$dir/${extra.stored}"
            when (extra.kind) {
                ExtraKind.DEVICE_DATA ->
                    attempt(Note.DEVICE_DATA_FAILED) { restoreInternal(transfer, archive, paths.deviceData, packageName) }
                ExtraKind.EXTERNAL_DATA ->
                    attempt(Note.EXTERNAL_DATA_FAILED) { restoreExternal(transfer, archive, packageName, sdk) }
            }
        }

        var granted = 0
        val wanted = if (options.permissions) manifest.permissions.filter { it.granted && Sh.isToken(it.name) } else emptyList()
        if (options.permissions) {
            task.progress(Stage.PERMISSIONS)
            granted = wanted.count { shell.run("pm grant --user 0 $packageName ${it.name}").ok }
            if (granted < wanted.size) notes += NoteDetail(Note.PERMISSIONS_PARTIAL, "$granted/${wanted.size}")
            val failed = manifest.appOps.filter { Parsers.isUserOp(it) }
                .filterNot { shell.run("cmd appops set --user 0 $packageName ${it.op} ${it.mode}").ok }
            if (failed.isNotEmpty()) notes += NoteDetail(Note.APP_OPS_FAILED, failed.joinToString { it.op })
            if (manifest.batteryExempt) shell.run("dumpsys deviceidle whitelist +$packageName")
        }
        shell.stopUnlessSystem(packageName)
        return RestoreOutcome(granted, wanted.size, notes)
    }

    private fun install(apks: List<BackupFile>, dir: String, sdk: Int) {
        val bypass = if (sdk >= 34) " --bypass-low-target-sdk-block" else ""
        val out = shell.run("pm install-create -r -d$bypass --user 0 -S ${apks.sumOf { it.size }}")
            .orThrow(ErrorCode.INSTALL_FAILED)
        val session = Regex("\\[(\\d+)]").find(out)?.groupValues?.get(1)
            ?: throw AppException(ErrorCode.INSTALL_FAILED, out)
        try {
            // Piped, because the installer may not read files under /storage itself.
            apks.forEachIndexed { index, apk ->
                pmOk(shell.run("cat ${q("$dir/${apk.stored}")} | pm install-write -S ${apk.size} $session $index.apk -"))
            }
            pmOk(shell.run("pm install-commit $session"))
        } catch (e: Exception) {
            shell.run("pm install-abandon $session")
            throw e
        }
    }

    private fun pmOk(result: RootResult) {
        val text = (result.out + "\n" + result.err).trim()
        if (!result.ok || "Success" !in text) throw AppException(ErrorCode.INSTALL_FAILED, text)
    }

    /**
     * Replaces the contents of an app's private folder. The folder itself was created by the
     * installer for the app's new uid, so its owner and SELinux label are the ones to apply.
     */
    private fun restoreInternal(transfer: Transfer, archive: String, parent: String, packageName: String) {
        val target = "$parent/$packageName"
        val stat = shell.run("stat -c '%u:%g %a' ${q(target)}").orThrow().trim()
        if (!stat.matches(Regex("\\d+:\\d+ \\d+"))) throw AppException(ErrorCode.COMMAND_FAILED, stat)
        val (owner, mode) = stat.split(' ')
        val label = shell.run("ls -dZ ${q(target)}").out.trim().substringBefore(' ')
        wipe(target)
        transfer.untar(archive, archive.endsWith(".zst"), "-x -C ${q(parent)}")
        shell.run("chown -hR $owner ${q(target)} && chmod $mode ${q(target)}").orThrow()
        if (label.startsWith("u:object_r:")) shell.run("chcon -hR ${q(label)} ${q(target)}").orThrow()
        else shell.run("restorecon -RF ${q(target)}").orThrow()
    }

    private fun wipe(target: String) {
        shell.run("find ${q(target)} -mindepth 1 -maxdepth 1 ! -name lib -exec rm -rf {} +").orThrow()
    }

    private fun appUid(packageName: String): String {
        val uid = shell.run("stat -c %u ${q("${paths.data}/$packageName")}").orThrow().trim()
        if (uid.toIntOrNull() == null) throw AppException(ErrorCode.COMMAND_FAILED, uid)
        return uid
    }

    /** Since Android 11 the app owns its folders there; before that media_rw does. */
    private fun ownExternal(target: String, packageName: String, group: String, sdk: Int) {
        val owner = if (sdk >= 30) "${appUid(packageName)}:$group" else "media_rw:media_rw"
        shell.run("chown -hR $owner ${q(target)} && restorecon -RF ${q(target)}").orThrow()
    }

    private fun restoreExternal(transfer: Transfer, archive: String, packageName: String, sdk: Int) {
        val target = "${paths.externalData}/$packageName"
        shell.run("mkdir -p ${q(target)}").orThrow()
        wipe(target)
        transfer.untar(archive, archive.endsWith(".zst"), "-x -C ${q(paths.externalData)}")
        ownExternal(target, packageName, "ext_data_rw", sdk)
    }

    private fun restoreObb(files: List<BackupFile>, dir: String, packageName: String, sdk: Int) {
        val target = "${paths.obb}/$packageName"
        shell.run("mkdir -p ${q(target)}").orThrow()
        for (file in files) {
            shell.run("cat ${q("$dir/${file.stored}")} > ${q("$target/${file.name}")}").orThrow()
        }
        ownExternal(target, packageName, "ext_obb_rw", sdk)
    }
}
