package com.aybrkaknc.easyandroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSetsTest {
    private fun set(kind: String, parent: String, vararg names: String, stored: String = "0.tar") = """
        {"format":"easyandroid-files","version":1,"kind":"$kind","appVersion":"1","createdAt":"2026-01-01T00:00:00+03:00",
        "device":{},"items":[{"parent":"$parent","names":[${names.joinToString(",") { "\"$it\"" }}],"stored":"$stored","size":3,"sha256":"ab"}]}
    """.trimIndent()

    @Test
    fun readsEveryKind() {
        val folders = FileSet.parse(set("media", "/storage/emulated/0", "DCIM", "My Pictures"))
        assertEquals(FileSetKind.MEDIA, folders.kind)
        assertEquals(3, folders.totalBytes)
        assertEquals(folders, FileSet.parse(folders.encode()))
        FileSet.parse(set("folders", "/storage/emulated/0", "Download"))
        FileSet.parse(set("wallpaper", "/data/system/users/0", "wallpaper", "wallpaper_lock"))
        FileSet.parse(set("calls", "/data/data/com.android.providers.contacts/databases", "calllog.db", "calllog.db-wal"))
        FileSet.parse(set("messages", "/data/user_de/0/com.android.providers.telephony/databases", "mmssms.db"))
    }

    @Test
    fun refusesPlacesThatAreNotTheirs() {
        val bad = listOf(
            set("media", "/data/data", "com.a"),
            set("media", "/storage/emulated/0", "Android"),
            set("media", "/storage/emulated/0", "EasyAndroid"),
            set("media", "/storage/emulated/0", ".hidden"),
            set("media", "/storage/emulated/0", "a/b"),
            set("media", "/storage/emulated/0", ".."),
            set("media", "/storage/emulated/0"),
            set("wallpaper", "/data/system/users/0", "packages.xml"),
            set("wallpaper", "/data/system/users/10", "wallpaper"),
            set("calls", "/data/user_de/0/com.android.providers.telephony/databases", "calllog.db"),
            set("calls", "/data/data/com.android.providers.contacts/databases", "contacts2.db"),
            set("messages", "/data/user_de/0/com.android.providers.telephony/databases", "../mmssms.db"),
            set("media", "/storage/emulated/0", "DCIM", stored = "../x.tar"),
            set("media", "/storage/emulated/0", "DCIM").replace("easyandroid-files", "other"),
            set("media", "/storage/emulated/0", "DCIM").replace("\"version\":1", "\"version\":2"),
            set("media", "/storage/emulated/0", "DCIM").replace(Regex("\"items\":.*"), "\"items\":[]}"),
            "[]",
        )
        for (text in bad) failsWith(ErrorCode.BACKUP_INVALID) { FileSet.parse(text) }
    }

    @Test
    fun choosesOnlyOrdinaryFolders() {
        assertTrue(FileSets.isChoosable("DCIM"))
        assertTrue(FileSets.isChoosable("My Documents"))
        for (bad in listOf("Android", "EasyAndroid", ".thumbnails", "", "a/b", "..")) assertFalse(bad, FileSets.isChoosable(bad))
    }

    @Test
    fun checksArchivesOfSeveralRoots() {
        val head = "-rw-rw-rw- shell/shell 2 2026-11-23 05:05"
        val roots = setOf("calllog.db", "calllog.db-wal")
        checkArchive("$head calllog.db\n$head calllog.db-wal", roots)
        checkArchive("drwxrwxrwx shell/shell 0 2026-11-23 05:05 DCIM/\n$head DCIM/a.jpg", setOf("DCIM"))
        for (bad in listOf("$head other.db", "$head calllog.db\n$head ../calllog.db-wal", "$head calllog.db-wal2", "")) {
            failsWith(ErrorCode.BACKUP_INVALID) { checkArchive(bad, roots) }
        }
        failsWith(ErrorCode.BACKUP_INVALID) {
            checkArchive("lrwxrwxrwx shell/shell 0 2026-11-23 05:05 calllog.db -> /data/system", roots)
        }
    }

    @Test
    fun listsFileSets() {
        val good = set("calls", "/data/data/com.android.providers.contacts/databases", "calllog.db")
        val out = "@@EA F calls_20260101-000000\n$good\n\n@@EA F media_20260101-000001\nnot json\n\n@@EA F other\nnot json\n"
        val list = BackupStore(FakeRoot("cd " to RootResult(0, out)), DEFAULT_ROOT).list()
        assertEquals("calls_20260101-000000", list.fileSets.single().id)
        assertEquals(FileSetKind.CALLS, list.fileSets.single().set.kind)
        assertEquals(listOf("media_20260101-000001"), list.broken)
    }

    @Test
    fun listsFoldersOfSharedStorage() {
        val out = "10\tDCIM\n5\tAndroid\n3\t.hidden\n7\tEasyAndroid\n2\tBackups\n\tEmpty\nnoise"
        val root = FakeRoot("cd " to RootResult(0, out))
        val engine = FileSetEngine(root, BackupStore(root, "/storage/emulated/0/Backups/Phone"), "1")
        val folders = engine.folders()
        assertEquals(listOf("DCIM", "Empty"), folders.map { it.name })
        assertEquals(10L * 1024, folders.first().bytes)
        assertEquals(0L, folders.last().bytes)
    }

    @Test
    fun backupsNeedAChoiceAndKnownFolders() {
        val root = FakeRoot("[ -e" to RootResult(1, ""))
        val engine = FileSetEngine(root, BackupStore(root, DEFAULT_ROOT), "1")
        val task = Task()
        failsWith(ErrorCode.INVALID_INPUT) { engine.backup(FileSetKind.MEDIA, emptyList(), DeviceSnapshot(), task) }
        failsWith(ErrorCode.NOT_FOUND) { engine.backup(FileSetKind.MEDIA, listOf("DCIM"), DeviceSnapshot(), task) }
        failsWith(ErrorCode.NOT_FOUND) { engine.backup(FileSetKind.CALLS, emptyList(), DeviceSnapshot(), task) }
        failsWith(ErrorCode.NOT_FOUND) { engine.backup(FileSetKind.MEDIA, listOf("Android"), DeviceSnapshot(), task) }
    }

    @Test
    fun findsTheNewestFileBackupOfAKind() {
        fun block(id: String, kind: String, parent: String, name: String, at: String) =
            "@@EA F $id\n" + set(kind, parent, name).replace("2026-01-01T00:00:00+03:00", at) + "\n\n"
        val db = "/data/data/com.android.providers.contacts/databases"
        val out = block("calls_old", "calls", db, "calllog.db", "2026-01-01T00:00:00+03:00") +
            block("calls_new", "calls", db, "calllog.db", "2026-03-01T00:00:00+03:00") +
            block("folders_x", "folders", "/storage/emulated/0", "Download", "2026-02-01T00:00:00+03:00") +
            block("media_y", "media", "/storage/emulated/0", "DCIM", "2026-04-01T00:00:00+03:00")
        val list = BackupStore(FakeRoot("cd " to RootResult(0, out)), DEFAULT_ROOT).list()
        assertEquals("calls_new", list.newestFileSet(FileSetKind.CALLS)?.id)
        assertEquals("media_y", list.newestFileSet(FileSetKind.FOLDERS, FileSetKind.MEDIA)?.id)
        assertEquals("folders_x", list.newestFileSet(FileSetKind.FOLDERS)?.id)
        assertNull(list.newestFileSet(FileSetKind.WALLPAPER))
    }
}
