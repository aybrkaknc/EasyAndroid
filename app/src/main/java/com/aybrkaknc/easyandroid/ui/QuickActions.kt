package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import com.aybrkaknc.easyandroid.AppInfo
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.PcRecord
import com.aybrkaknc.easyandroid.core.StoredBackup

/** The choices of the apps sheet. Each one picks its apps with [QuickTargets]. */
enum class QuickAction(@param:StringRes val title: Int) {
    BACKUP_ALL(R.string.action_backup_all),
    BACKUP_PENDING(R.string.action_backup_pending),
    BACKUP_UPDATED(R.string.action_backup_updated),
    BACKUP_REDO(R.string.action_backup_redo),
    RESTORE_ALL(R.string.action_restore_all),
    RESTORE_MISSING(R.string.action_restore_missing),
    RESTORE_NEWER(R.string.action_restore_newer),
    DELETE_MISSING(R.string.action_delete_missing),
}

/**
 * What a bulk action covers. Packages the phone cannot do without are left out, because a backup
 * closes the app and restoring their data on another ROM can stop it from starting. They can still
 * be selected by hand.
 *
 * [backups] is sorted newest first.
 */
class QuickTargets(
    private val apps: List<AppInfo>,
    private val backups: List<StoredBackup>,
    pc: List<PcRecord> = emptyList(),
    private val isProtected: (String) -> Boolean,
) {
    /** What the PC holds counts as saved too, so that the same app is not asked for twice. */
    private val held = pc.groupBy { it.packageName }.mapValues { (_, records) -> records.maxBy { it.createdAt } }
    private val installed = apps.filter { it.installed }.associateBy { it.packageName }
    private val newest = backups.distinctBy { it.manifest.packageName }.associateBy { it.manifest.packageName }

    fun backup(action: QuickAction): List<AppInfo> = installed.values.filterNot { isProtected(it.packageName) }.filter { app ->
        val saved = newest[app.packageName]
        when (action) {
            QuickAction.BACKUP_ALL -> true
            QuickAction.BACKUP_PENDING -> saved == null && app.packageName !in held
            QuickAction.BACKUP_UPDATED -> {
                val known = listOfNotNull(saved?.manifest?.versionName, held[app.packageName]?.versionName)
                known.isNotEmpty() && known.all { isNewer(app.versionName, it) }
            }
            QuickAction.BACKUP_REDO -> saved != null
            else -> false
        }
    }

    fun restore(action: QuickAction): List<StoredBackup> = newest.values.filterNot { isProtected(it.manifest.packageName) }.filter { backup ->
        val app = installed[backup.manifest.packageName]
        when (action) {
            QuickAction.RESTORE_ALL -> true
            QuickAction.RESTORE_MISSING -> app == null
            QuickAction.RESTORE_NEWER -> app != null && isNewer(backup.manifest.versionName.orEmpty(), app.versionName)
            else -> false
        }
    }

    /** Every backup of apps that are not installed. Empty when the app list is, so that a failed scan deletes nothing. */
    fun deletable(): List<StoredBackup> =
        if (installed.isEmpty()) emptyList() else backups.filter { it.manifest.packageName !in installed }
}

/** Compares the leading dotted numbers. False when either version has none. */
fun isNewer(version: String, than: String): Boolean {
    val a = numbers(version) ?: return false
    val b = numbers(than) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0L }
        val y = b.getOrElse(i) { 0L }
        if (x != y) return x > y
    }
    return false
}

private fun numbers(version: String): List<Long>? {
    val text = version.trim().removePrefix("v").takeWhile { it in '0'..'9' || it == '.' }.trim('.')
    val parts = text.split('.').map { it.toLongOrNull() }
    return if (parts.any { it == null }) null else parts.filterNotNull()
}
