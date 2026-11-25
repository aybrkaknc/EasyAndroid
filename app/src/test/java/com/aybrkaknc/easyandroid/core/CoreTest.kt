package com.aybrkaknc.easyandroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime

fun failsWith(code: ErrorCode, block: () -> Unit) {
    try {
        block()
    } catch (e: AppException) {
        assertEquals(code, e.code)
        return
    }
    throw AssertionError("expected $code")
}

/** Answers commands by substring, records everything that was run. */
class FakeRoot(private vararg val answers: Pair<String, RootResult>) : Root {
    val calls = ArrayList<String>()

    override fun run(script: String): RootResult {
        calls += script
        return answers.firstOrNull { it.first in script }?.second ?: RootResult(0, "")
    }

    override fun open(script: String): RootStream = throw UnsupportedOperationException(script)
}

class CoreTest {
    @Test
    fun quotesOnlyWhenNeeded() {
        assertEquals("com.example.app", Sh.quote("com.example.app"))
        assertEquals("'my file.txt'", Sh.quote("my file.txt"))
        assertEquals("'a'\\''b'", Sh.quote("a'b"))
        assertEquals("'$(reboot)'", Sh.quote("$(reboot)"))
        assertEquals("''", Sh.quote(""))
    }

    @Test
    fun validatesNamesAndPaths() {
        assertTrue(Sh.isPackage("com.example_1.app2"))
        for (bad in listOf("nodots", "com.evil; reboot", "", "com.a\n")) assertFalse(bad, Sh.isPackage(bad))
        for (bad in listOf("../x", "/abs", "a/../b", "C:/x", "a\\b", "", "a//b")) {
            failsWith(ErrorCode.INVALID_INPUT) { Sh.checkRelative(bad) }
        }
        for (bad in listOf("/", "rel", "/sdcard/../data", "/a\nb")) failsWith(ErrorCode.INVALID_INPUT) { Sh.checkPath(bad) }
        for (bad in listOf("..", ".", "a/b", "")) failsWith(ErrorCode.INVALID_INPUT) { Sh.checkName(bad) }
    }

    /** Written by EasyADB 0.2.0's serializer. */
    private val easyAdb = """
        {"formatVersion":1,"appVersion":"0.2.0","package":"com.a","label":null,"versionName":"1.0",
        "createdAt":"2026-01-01T00:00:00.123456700+03:00",
        "device":{"model":"M","serial":"S","androidVersion":"16","sdk":36},
        "files":[{"kind":"apk","stored":"apk/base.apk","name":"base.apk","size":3,"sha256":"ab"},
        {"kind":"data","stored":"data.tar","name":"data.tar","size":4,"sha256":"cd"}],
        "permissions":[{"name":"android.permission.CAMERA","granted":true}]}
    """.trimIndent()

    @Test
    fun readsEasyAdbManifest() {
        val manifest = Manifest.parse(easyAdb)
        assertEquals("com.a", manifest.packageName)
        assertEquals(7, manifest.totalBytes)
        assertTrue(manifest.has(FileKind.DATA))
        assertTrue(parseTimestamp(manifest.createdAt) != null)
    }

    @Test
    fun writesFieldsEasyAdbNeeds() {
        val text = Manifest.parse(easyAdb).encode()
        for (field in listOf("formatVersion", "appVersion", "package", "label", "versionName", "createdAt", "device", "files",
            "permissions", "androidVersion", "serial", "kind", "stored", "sha256")) {
            assertTrue(field, "\"$field\"" in text)
        }
        assertTrue("\"kind\": \"apk\"" in text)
        assertEquals(Manifest.parse(easyAdb), Manifest.parse(text))
    }

    @Test
    fun rejectsBadManifests() {
        failsWith(ErrorCode.BACKUP_INVALID) { Manifest.parse("{}") }
        failsWith(ErrorCode.BACKUP_INVALID) { Manifest.parse("not json") }
        failsWith(ErrorCode.BACKUP_INVALID) { Manifest.parse(easyAdb.replace("\"formatVersion\":1", "\"formatVersion\":9")) }
        failsWith(ErrorCode.INVALID_INPUT) { Manifest.parse(easyAdb.replace("apk/base.apk", "../base.apk")) }
        failsWith(ErrorCode.INVALID_INPUT) { Manifest.parse(easyAdb.replace("\"com.a\"", "\"com.a;reboot\"")) }
    }

    @Test
    fun namesFolders() {
        assertEquals("com.a_20260102-030405", folderName("com.a", OffsetDateTime.parse("2026-01-02T03:04:05+03:00")))
        assertEquals("2026-01-02T03:04:05.006+03:00", timestamp(OffsetDateTime.parse("2026-01-02T03:04:05.006789+03:00")))
    }

    @Test
    fun parsesRuntimePermissionsOnly() {
        val dump = """
            Packages:
              Package [com.a] (123):
                versionName=1.2.3
                install permissions:
                  android.permission.INTERNET: granted=true
                User 0: ceDataInode=1 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ USER_SET ]
                    android.permission.ACCESS_FINE_LOCATION: granted=false, flags=[ USER_SET ]
                  disabledComponents:
                    com.a.Foo
        """.trimIndent()
        assertEquals(
            listOf(
                PermissionGrant("android.permission.ACCESS_FINE_LOCATION", false),
                PermissionGrant("android.permission.CAMERA", true),
            ),
            Parsers.runtimePermissions(dump),
        )
        assertEquals("1.2.3", Parsers.versionName(dump))
        assertTrue(Parsers.runtimePermissions("").isEmpty())
        assertNull(Parsers.versionName(""))
    }

    @Test
    fun parsesAppOps() {
        val out = """
            Uid mode: COARSE_LOCATION: ignore
            MANAGE_EXTERNAL_STORAGE: allow
            SYSTEM_ALERT_WINDOW: allow; time=+1h2m ago
            RUN_ANY_IN_BACKGROUND: ignore
            WRITE_SETTINGS: default
            MANAGE_EXTERNAL_STORAGE: deny
            WAKE_LOCK: allow
        """.trimIndent()
        assertEquals(
            listOf(
                AppOp("MANAGE_EXTERNAL_STORAGE", "allow"),
                AppOp("SYSTEM_ALERT_WINDOW", "allow"),
                AppOp("RUN_ANY_IN_BACKGROUND", "ignore"),
            ),
            Parsers.appOps(out),
        )
        assertTrue(Parsers.appOps("No operations.").isEmpty())
        assertFalse(Parsers.isUserOp(AppOp("WRITE_SETTINGS", "allow; reboot")))
    }

    @Test
    fun parsesBatteryExemptions() {
        val out = "system-excidle,com.android.vending,10252\nsystem,com.b,1\nuser,com.a,10300\n"
        assertEquals(setOf("com.a"), Parsers.batteryExempt(out))
    }

    @Test
    fun parsesSettings() {
        val out = "a=1\nb=x=y\nempty=\nnothing=null\nmulti=first\nsecond line\nlast=ok"
        assertEquals(mapOf("a" to "1", "b" to "x=y", "empty" to "", "last" to "ok"), Parsers.settings(out))
    }

    @Test
    fun parsesWifi() {
        fun network(ssid: String, key: String, psk: String) =
            "<Network><WifiConfiguration><string name=\"ConfigKey\">&quot;$ssid&quot;$key</string>" +
                "<string name=\"SSID\">&quot;$ssid&quot;</string>$psk</WifiConfiguration></Network>"
        val xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><WifiConfigStoreData><NetworkList>" +
            network("Ev & Ofis", "WPA_PSK", "<string name=\"PreSharedKey\">&quot;p&apos;ss word&quot;</string>").replace("Ev & ", "Ev &amp; ") +
            network("Cafe", "NONE", "<null name=\"PreSharedKey\" />") +
            network("New", "SAE", "<string name=\"PreSharedKey\">&quot;secret&quot;</string>") +
            network("Work", "WPA_EAP", "<null name=\"PreSharedKey\" />") +
            network("Hex", "WPA_PSK", "<string name=\"PreSharedKey\">" + "ab".repeat(32) + "</string>") +
            "</NetworkList></WifiConfigStoreData>"
        assertEquals(
            listOf(
                WifiNetwork("Ev & Ofis", "wpa2", "p'ss word"),
                WifiNetwork("Cafe", "open"),
                WifiNetwork("New", "wpa3", "secret"),
            ),
            Parsers.wifi(xml),
        )
    }

    @Test
    fun parsesFreeSpace() {
        assertEquals(8060076L * 1024, BackupStore.parseDf("/dev/fuse       10167132 1959600   8060076  20% /storage/emulated"))
        assertNull(BackupStore.parseDf("df: /x: No such file"))
    }

    @Test
    fun switchesWirelessDebugging() {
        val root = FakeRoot()
        val wireless = WirelessDebug(root)
        wireless.setEnabled(true)
        wireless.setEnabled(false)
        assertEquals(listOf("settings put global adb_wifi_enabled 1", "settings put global adb_wifi_enabled 0"), root.calls)
    }

    @Test
    fun readsTheWirelessDebuggingPort() {
        val ss = "State  Recv-Q Send-Q Local Address:Port Peer Address:Port Process\n" +
            "LISTEN      0      4            *:42599                    *:*                   users:((\"adbd\",pid=14104,fd=28))\n" +
            "LISTEN      0      4            *:5555                     *:*                   users:((\"adbd\",pid=14104,fd=30))\n" +
            "LISTEN      0      50           *:8080                     *:*                   users:((\"other\",pid=1,fd=3))"
        assertEquals(42599, WirelessDebug.parsePort(ss))
        assertEquals(42599, WirelessDebug.parsePort("tcp6  0  0 [::]:42599  [::]:*  LISTEN  14104/adbd"))
        assertEquals(5555, WirelessDebug.parsePort("LISTEN 0 4 *:5555 *:* users:((\"adbd\",pid=1,fd=2))"))
        assertNull(WirelessDebug.parsePort("LISTEN 0 50 *:8080 *:* users:((\"other\",pid=1,fd=3))\n"))
        assertNull(WirelessDebug.parsePort(""))
        val root = FakeRoot("ss -ltnp" to RootResult(0, ss))
        assertEquals(42599, WirelessDebug(root).listeningPort())
    }

    @Test
    fun opensPairingScreensOrReportsMissingOnes() {
        val root = FakeRoot()
        WirelessDebug(root).openQrPairing()
        WirelessDebug(root).openPage()
        assertEquals("am start --user 0 -n com.android.settings/.development.AdbQrCodeActivity", root.calls[0])
        assertTrue(":settings:show_fragment com.android.settings.development.AdbWirelessDebuggingFragment" in root.calls[1])
        // `am` prints the error and still exits with 0.
        val missing = FakeRoot("am start" to RootResult(0, "Error type 3\nError: Activity class {x/y} does not exist."))
        failsWith(ErrorCode.NOT_FOUND) { WirelessDebug(missing).openQrPairing() }
    }

    @Test
    fun mapsPickedFoldersToPaths() {
        val authority = "com.android.externalstorage.documents"
        assertEquals("/storage/emulated/0/My Backups", treePath(authority, "primary:My Backups"))
        assertEquals("/storage/emulated/0", treePath(authority, "primary:"))
        assertEquals("/storage/12AB-34CD/x/y", treePath(authority, "12AB-34CD:x/y"))
        assertNull(treePath(authority, "primary:../../data"))
        assertNull(treePath(authority, "../data:x"))
        assertNull(treePath("com.google.android.apps.docs.storage", "primary:x"))
    }

    @Test
    fun acceptsOrdinaryArchives() {
        checkArchive(
            """
            drwxrwxrwx shell/shell       0 2026-11-23 05:05 com.a/
            -rw-rw-rw- u0_a1/u0_a1       2 2026-11-23 05:05 com.a/d e/f -> x
            lrwxrwxrwx shell/shell       0 2026-11-23 05:05 com.a/l -> /data/x
            """.trimIndent(),
            "com.a",
        )
    }

    @Test
    fun rejectsEscapingArchives() {
        val head = "-rw-rw-rw- shell/shell 2 2026-11-23 05:05"
        val bad = listOf(
            "",
            "$head evil",
            "$head com.ab/x",
            "$head com.a/../x",
            "$head com.b/x com.a/y",
            "$head evil com.a/y",
            "lrwxrwxrwx shell/shell 0 2026-11-23 05:05 com.a/l -> /data/system\n$head com.a/l/x",
            "lrwxrwxrwx shell/shell 0 2026-11-23 05:05 com.a -> /data/system",
            "hrw-rw-rw- shell/shell 0 2026-11-23 05:05 com.a/h link to com.b/f",
            "crw-rw-rw- shell/shell 0 2026-11-23 05:05 com.a/dev",
        )
        for (listing in bad) failsWith(ErrorCode.BACKUP_INVALID) { checkArchive(listing, "com.a") }
    }

    private val database by lazy {
        Debloat.parseDatabase(File("src/main/assets/debloat_db.json").readText())
    }

    @Test
    fun loadsUadDatabase() {
        assertTrue(database.size > 5000)
        assertEquals(Removal.UNSAFE, database["android"]?.removal)
    }

    @Test
    fun debloatRunsCommandsAndReportsFailures() {
        val root = FakeRoot("uninstall" to RootResult(0, "Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        val debloat = Debloat(root, emptyMap(), "com.own")
        debloat.apply(ProfileEntry("com.a", DebloatAction.DISABLE))
        assertEquals("pm disable-user --user 0 com.a", root.calls.last())
        failsWith(ErrorCode.COMMAND_FAILED) { debloat.apply(ProfileEntry("com.a", DebloatAction.UNINSTALL)) }
        failsWith(ErrorCode.INVALID_INPUT) { debloat.apply(ProfileEntry("com.a;reboot", DebloatAction.DISABLE)) }
    }

    @Test
    fun debloatClearsDataBeforeRemovingAndStopsOnFailure() {
        val root = FakeRoot()
        Debloat(root, emptyMap(), "com.own").apply(ProfileEntry("com.a", DebloatAction.DISABLE))
        assertEquals(listOf("pm clear --user 0 com.a", "pm disable-user --user 0 com.a"), root.calls)

        val failing = FakeRoot("pm clear" to RootResult(1, "Failure"))
        failsWith(ErrorCode.COMMAND_FAILED) { Debloat(failing, emptyMap(), "com.own").apply(ProfileEntry("com.a", DebloatAction.UNINSTALL)) }
        assertEquals(listOf("pm clear --user 0 com.a"), failing.calls)

        val enabling = FakeRoot()
        Debloat(enabling, emptyMap(), "com.own").apply(ProfileEntry("com.a", DebloatAction.ENABLE))
        assertEquals(listOf("pm enable --user 0 com.a"), enabling.calls)
    }

    @Test
    fun debloatProtectsUnsafePackages() {
        val root = FakeRoot()
        val unsafe = database.entries.first { it.value.removal == Removal.UNSAFE && Sh.isPackage(it.key) }.key
        val debloat = Debloat(root, database, "com.own.app")
        for (name in listOf(unsafe, "com.own.app", "com.android.systemui")) {
            failsWith(ErrorCode.PROTECTED) { debloat.apply(ProfileEntry(name, DebloatAction.UNINSTALL)) }
        }
        assertTrue(root.calls.isEmpty())
        debloat.apply(ProfileEntry(unsafe, DebloatAction.ENABLE))
    }

    @Test
    fun readsEasyAdbProfiles() {
        val good = """{"format":"easyadb-debloat-profile","version":1,"name":"x","createdAt":"","entries":[{"package":"com.a","action":"disable"}]}"""
        val profile = DebloatProfile.parse(good)
        assertEquals(ProfileEntry("com.a", DebloatAction.DISABLE), profile.entries.single())
        assertEquals(profile, DebloatProfile.parse(profile.encode()))
        failsWith(ErrorCode.INVALID_INPUT) { DebloatProfile.parse(good.replace("com.a", "com.a; reboot")) }
        failsWith(ErrorCode.BACKUP_INVALID) { DebloatProfile.parse(good.replace("easyadb-debloat-profile", "other")) }
        failsWith(ErrorCode.BACKUP_INVALID) { DebloatProfile.parse("[]") }
    }

    @Test
    fun listsBackups() {
        val out = "@@EA D com.a_20260101-000000\n$easyAdb\n\n@@EA D com.b_20260101-000000\n\n@@EA D random\n\n@@EA S system_20260101-000000.json"
        val list = BackupStore(FakeRoot("cd " to RootResult(0, out)), DEFAULT_ROOT).list()
        assertEquals("com.a_20260101-000000", list.backups.single().id)
        assertEquals(listOf("com.b_20260101-000000"), list.broken)
        assertEquals(listOf("system_20260101-000000.json"), list.systemSnapshots)
    }

    @Test
    fun systemBackupNeverTouchesIdentity() {
        for (key in listOf("android_id", "device_provisioned", "adb_enabled", "bad key; reboot", "lock_screen_owner_info")) {
            assertFalse(key, SystemBackup.isRestorable(key))
        }
        assertTrue(SystemBackup.isRestorable("font_scale"))
        val root = FakeRoot()
        val backup = SystemBackup(root, BackupStore(root, DEFAULT_ROOT))
        failsWith(ErrorCode.PROTECTED) { backup.apply(SystemItem.Setting("secure", "android_id", "x", null)) }
        backup.apply(SystemItem.Setting("system", "font_scale", "1.1; reboot", null))
        assertEquals("settings put --user 0 system font_scale '1.1; reboot'", root.calls.last())
    }
}
