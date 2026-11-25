package com.aybrkaknc.easyandroid.ui

import androidx.annotation.StringRes
import com.aybrkaknc.easyandroid.AppInfo
import com.aybrkaknc.easyandroid.R
import com.aybrkaknc.easyandroid.core.StoredBackup

enum class QuickSection(@param:StringRes val title: Int, @param:StringRes val hint: Int? = null) {
    BACKUP(R.string.section_quick_backup),
    RESTORE(R.string.section_quick_restore, R.string.section_quick_restore_hint),
    OTHER(R.string.section_other),
}

enum class QuickButtons { NONE, TO, FROM, DELETE }

/** [id] is stored in the preferences of pinned actions, so it must not change. */
enum class QuickAction(
    val id: String,
    val section: QuickSection,
    @param:StringRes val title: Int,
    @param:StringRes val text: Int,
    val buttons: QuickButtons,
    val root: Boolean = false,
) {
    BACKUP_ALL("backup_all", QuickSection.BACKUP, R.string.action_backup_all, R.string.action_backup_all_text, QuickButtons.NONE),
    BACKUP_PENDING("backup_pending", QuickSection.BACKUP, R.string.action_backup_pending, R.string.action_backup_pending_text, QuickButtons.TO),
    BACKUP_UPDATED("backup_updated", QuickSection.BACKUP, R.string.action_backup_updated, R.string.action_backup_updated_text, QuickButtons.TO),
    BACKUP_REDO("backup_redo", QuickSection.BACKUP, R.string.action_backup_redo, R.string.action_backup_redo_text, QuickButtons.TO),
    RESTORE_ALL("restore_all", QuickSection.RESTORE, R.string.action_restore_all, R.string.action_restore_all_text, QuickButtons.FROM, root = true),
    RESTORE_MISSING("restore_missing", QuickSection.RESTORE, R.string.action_restore_missing, R.string.action_restore_missing_text, QuickButtons.FROM, root = true),
    RESTORE_NEWER("restore_newer", QuickSection.RESTORE, R.string.action_restore_newer, R.string.action_restore_newer_text, QuickButtons.FROM, root = true),
    DELETE_MISSING("delete_missing", QuickSection.OTHER, R.string.action_delete_missing, R.string.action_delete_missing_text, QuickButtons.DELETE),
    TOGGLE("toggle", QuickSection.OTHER, R.string.action_toggle, R.string.action_toggle_text, QuickButtons.NONE, root = true),
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
    private val isProtected: (String) -> Boolean,
) {
    private val installed = apps.filter { it.installed }.associateBy { it.packageName }
    private val newest = backups.distinctBy { it.manifest.packageName }.associateBy { it.manifest.packageName }

    fun backup(action: QuickAction): List<AppInfo> = installed.values.filterNot { isProtected(it.packageName) }.filter { app ->
        val saved = newest[app.packageName]
        when (action) {
            QuickAction.BACKUP_ALL -> true
            QuickAction.BACKUP_PENDING -> saved == null
            QuickAction.BACKUP_UPDATED -> saved != null && isNewer(app.versionName, saved.manifest.versionName.orEmpty())
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
