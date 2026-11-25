package com.aybrkaknc.easyandroid.ui

import com.aybrkaknc.easyandroid.AppInfo
import com.aybrkaknc.easyandroid.core.DeviceSnapshot
import com.aybrkaknc.easyandroid.core.Manifest
import com.aybrkaknc.easyandroid.core.PcRecord
import com.aybrkaknc.easyandroid.core.StoredBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {
    private fun app(name: String, version: String, installed: Boolean = true) =
        AppInfo(name, name, version, system = false, installed = installed, enabled = true, apkBytes = 0, debloat = null)

    private fun backup(id: String, name: String, version: String?) = StoredBackup(
        id, Manifest(1, "0", name, versionName = version, createdAt = "", device = DeviceSnapshot(), files = emptyList()),
    )

    private val apps = listOf(
        app("a.fresh", "1.0"), app("a.updated", "2.0"), app("a.older", "1.0"), app("a.same", "1.0"),
        app("a.removed", "1.0", installed = false), app("a.core", "1.0"),
    )

    // Newest first, as the store sorts them.
    private val backups = listOf(
        backup("updated", "a.updated", "1.9"), backup("older", "a.older", "1.10"), backup("same-new", "a.same", "1.0"),
        backup("same-old", "a.same", "0.5"), backup("removed", "a.removed", "1.0"), backup("gone", "a.gone", null),
        backup("core", "a.core", "0.1"),
    )
    private val targets = QuickTargets(apps, backups) { it == "a.core" }

    private fun backed(action: QuickAction) = targets.backup(action).map { it.packageName }
    private fun restored(action: QuickAction) = targets.restore(action).map { it.id }

    @Test
    fun backupTargets() {
        assertEquals(listOf("a.fresh", "a.updated", "a.older", "a.same"), backed(QuickAction.BACKUP_ALL))
        assertEquals(listOf("a.fresh"), backed(QuickAction.BACKUP_PENDING))
        assertEquals(listOf("a.updated"), backed(QuickAction.BACKUP_UPDATED))
        assertEquals(listOf("a.updated", "a.older", "a.same"), backed(QuickAction.BACKUP_REDO))
    }

    @Test
    fun whatThePcHoldsCountsAsSaved() {
        val pc = listOf(PcRecord("a.fresh", "1.0", "2026-01-01"), PcRecord("a.updated", "2.0", "2026-01-02"))
        val held = QuickTargets(apps, backups, pc) { it == "a.core" }
        // The app that only the PC has is no longer pending, and the one it has in a newer version is up to date.
        assertEquals(emptyList<String>(), held.backup(QuickAction.BACKUP_PENDING).map { it.packageName })
        assertEquals(emptyList<String>(), held.backup(QuickAction.BACKUP_UPDATED).map { it.packageName })
        // Backing up again only looks at the phone's own backups.
        assertEquals(listOf("a.updated", "a.older", "a.same"), held.backup(QuickAction.BACKUP_REDO).map { it.packageName })
    }

    @Test
    fun restoreTargets() {
        assertEquals(listOf("updated", "older", "same-new", "removed", "gone"), restored(QuickAction.RESTORE_ALL))
        assertEquals(listOf("removed", "gone"), restored(QuickAction.RESTORE_MISSING))
        assertEquals(listOf("older"), restored(QuickAction.RESTORE_NEWER))
    }

    @Test
    fun deletesOnlyBackupsOfMissingApps() {
        assertEquals(listOf("removed", "gone"), targets.deletable().map { it.id })
        assertTrue(QuickTargets(emptyList(), backups) { false }.deletable().isEmpty())
    }

    @Test
    fun comparesVersions() {
        assertTrue(isNewer("1.10", "1.9"))
        assertTrue(isNewer("v2", "1.9.9"))
        assertTrue(isNewer("1.2.1-beta", "1.2 (34)"))
        assertFalse(isNewer("1.0", "1.0.0"))
        assertFalse(isNewer("Varies", "1.0"))
        assertFalse(isNewer("1.0", ""))
        assertFalse(isNewer("1..2", "1.0"))
    }
}
