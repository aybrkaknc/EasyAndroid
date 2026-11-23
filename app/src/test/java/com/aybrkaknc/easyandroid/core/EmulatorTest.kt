package com.aybrkaknc.easyandroid.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Speaks adb's shell protocol directly, because `adb.exe shell` translates line endings on Windows.
 * Packets are one id byte, a little endian length and the payload.
 */
private class AdbStream(serial: String, command: String) : RootStream {
    private val socket = Socket("127.0.0.1", 5037)
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = socket.getOutputStream()
    private val errors = StringBuilder()
    private var exitCode = -1
    private val pipe = PipedOutputStream()
    override val stdout = PipedInputStream(pipe, 1 shl 20)
    override val stdin = object : OutputStream() {
        private var closed = false
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len > 0) packet(0, b.copyOfRange(off, off + len))
        }
        override fun close() {
            if (!closed) packet(4, ByteArray(0))
            closed = true
        }
    }
    private val pump: Thread

    init {
        request("host:transport:$serial")
        request("shell,v2,raw:$command")
        pump = thread(isDaemon = true) {
            runCatching {
                while (true) {
                    val id = input.readUnsignedByte()
                    val data = ByteArray(Integer.reverseBytes(input.readInt())).also { input.readFully(it) }
                    when (id) {
                        1 -> pipe.write(data)
                        2 -> errors.append(String(data))
                        3 -> {
                            exitCode = data[0].toInt()
                            break
                        }
                    }
                }
            }
            runCatching { pipe.close() }
        }
    }

    private fun request(text: String) {
        output.write("%04x%s".format(text.toByteArray().size, text).toByteArray())
        check(String(ByteArray(4).also { input.readFully(it) }) == "OKAY") { text }
    }

    @Synchronized
    private fun packet(id: Int, data: ByteArray) {
        output.write(ByteBuffer.allocate(5 + data.size).order(ByteOrder.LITTLE_ENDIAN).put(id.toByte()).putInt(data.size).put(data).array())
    }

    override fun finish(): RootResult {
        pump.join()
        return RootResult(exitCode, "", errors.toString())
    }

    override fun close() = socket.close()
}

/** The shell user may not relabel files, so `chcon` is a stub here. */
private class AdbRoot(private val serial: String) : Root {
    override fun run(script: String): RootResult = AdbStream(serial, "chcon() { :; }\n(\n$script\n) </dev/null").use { stream ->
        val out = stream.stdout.bufferedReader().readText().trimEnd('\n')
        val result = stream.finish()
        RootResult(result.code, out, result.err)
    }

    override fun open(script: String): RootStream = AdbStream(serial, script)
}

/**
 * Runs the engines against a real Android shell. Skipped unless EA_SERIAL and EA_PACKAGE are set.
 * Only emulators are accepted. Without root the private folders are stand-ins.
 */
class EmulatorTest {
    private val base = "/data/local/tmp/easyandroid"
    private val paths = Paths("$base/data", "$base/de", "$base/ext", "$base/obb")
    private lateinit var root: Root
    private lateinit var packageName: String
    private lateinit var store: BackupStore

    @Before
    fun setUp() {
        val serial = System.getenv("EA_SERIAL")
        val name = System.getenv("EA_PACKAGE")
        assumeTrue(serial != null && name != null && serial.startsWith("emulator-"))
        root = AdbRoot(serial!!)
        packageName = name!!
        store = BackupStore(root, "/storage/emulated/0/EasyAndroidTest/Back ups")
        root.run(
            "rm -rf $base; for d in data de ext obb; do mkdir -p $base/${'$'}d/$packageName; done; cd $base/data/$packageName; " +
                "mkdir -p files cache 'sp ace'; echo hello > files/a; echo x > cache/c; echo y > 'sp ace/b c'; ln -s /data/app lib; " +
                "head -c 5000000 /dev/urandom > files/big; echo de > $base/de/$packageName/d; echo obb > $base/obb/$packageName/main.obb",
        ).orThrow()
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.run("rm -rf $base /storage/emulated/0/EasyAndroidTest")
    }

    private fun text(path: String) = root.run("cat '$base/$path'").orThrow()

    private fun roundTrip(compress: Boolean) {
        val engine = BackupEngine(root, store, "test", paths)
        val device = engine.snapshot()
        assertTrue(device.sdk >= 29)
        var last = 0L
        val task = Task { _, done, _ -> last = done }
        val outcome = engine.backup(packageName, "Label", BackupOptions(externalData = false, compress = compress), device, task)
        assertEquals(outcome.notes.joinToString { it.detail }, 0, outcome.notes.size)
        assertTrue(last > 5_000_000)
        assertEquals(if (compress) FORMAT_ZSTD else FORMAT_PLAIN, outcome.manifest.formatVersion)
        assertTrue(outcome.manifest.has(FileKind.OBB) && outcome.manifest.has(FileKind.DATA))
        assertEquals(outcome.manifest, store.list().backups.single().manifest)

        val before = root.run("sha256sum $base/data/$packageName/files/big").orThrow()
        root.run("cd $base; rm data/$packageName/files/a; echo stale > data/$packageName/files/stale; echo bad > data/$packageName/files/big").orThrow()

        // Chowning to the media groups needs root, so OBB is expected to be noted here.
        val result = RestoreEngine(root, store, paths).restore(outcome.id, RestoreOptions(obb = false), device.sdk, Task())
        assertEquals(result.notes.joinToString { "${it.note} ${it.detail}" }, 0, result.notes.count { it.note != Note.PERMISSIONS_PARTIAL })
        assertEquals("hello", text("data/$packageName/files/a"))
        assertEquals("y", text("data/$packageName/sp ace/b c"))
        assertEquals("de", text("de/$packageName/d"))
        assertEquals(before, root.run("sha256sum $base/data/$packageName/files/big").orThrow())
        assertEquals("gone", root.run("[ -e $base/data/$packageName/files/stale ] || echo gone").out)
        assertEquals("gone", root.run("[ -e $base/data/$packageName/cache/c ] || echo gone").out)
        assertTrue(root.run("pm path --user 0 $packageName").ok)
    }

    @Test
    fun plainRoundTrip() = roundTrip(false)

    @Test
    fun zstdRoundTrip() = roundTrip(true)

    @Test
    fun exportsAndImports() {
        val engine = BackupEngine(root, store, "test", paths)
        val id = engine.backup(packageName, null, BackupOptions(), DeviceSnapshot(), Task()).id
        val archive = Archive(root, store, Task())
        val bytes = ByteArrayOutputStream().also { archive.export(id, it) }.toByteArray()
        val copy = archive.import(ByteArrayInputStream(bytes))
        assertEquals("$id-1", copy)
        assertEquals(store.read(id), store.read(copy))
        val data = store.read(copy).files.first { it.kind == FileKind.DATA }
        Transfer(root, Task()).verify(store.dir(copy) + "/data.tar", data.size, data.sha256) {}
        failsWith(ErrorCode.BACKUP_INVALID) { archive.import(ByteArrayInputStream(ByteArray(0))) }
        assertEquals(2, store.list().backups.size)
        System.getenv("EA_EXPORT")?.let { java.io.File(it).writeBytes(bytes) }
    }

    @Test
    fun detectsCorruptionBeforeTouchingData() {
        val id = BackupEngine(root, store, "test", paths)
            .backup(packageName, null, BackupOptions(), DeviceSnapshot(), Task()).id
        root.run("echo x >> ${Sh.quote(store.dir(id) + "/data.tar")}").orThrow()
        failsWith(ErrorCode.INTEGRITY) { RestoreEngine(root, store, paths).restore(id, RestoreOptions(), 36, Task()) }
        assertEquals("hello", text("data/$packageName/files/a"))
    }

    @Test
    fun warnsWhenPrivateDataIsInvisible() {
        root.run("rm -rf $base/data").orThrow()
        val outcome = BackupEngine(root, store, "test", paths)
            .backup(packageName, null, BackupOptions(), DeviceSnapshot(), Task())
        assertEquals(listOf(Note.DATA_FAILED), outcome.notes.map { it.note })
    }

    @Test
    fun cancelsAndCleansUp() {
        var bytes = 0L
        val task = Task({ bytes > 0 }) { stage, done, _ -> if (stage == Stage.DATA) bytes = done }
        failsWith(ErrorCode.CANCELLED) {
            BackupEngine(root, store, "test", paths).backup(packageName, null, BackupOptions(), DeviceSnapshot(), task)
        }
        TimeUnit.MILLISECONDS.sleep(500)
        val list = store.list()
        assertEquals(0, list.backups.size + list.broken.size)
    }

    @Test
    fun systemSnapshotDiffsAndApplies() {
        val system = SystemBackup(root, store)
        val original = root.run("settings get --user 0 system font_scale").out
        try {
            root.run("settings put --user 0 system font_scale 1.15").orThrow()
            val name = system.create(settings = true, roles = true, wifi = false, device = DeviceSnapshot())
            assertEquals(listOf(name), store.list().systemSnapshots)
            val snapshot = SystemSnapshot.parse(store.readText(name))
            assertTrue(snapshot.settings.getValue("system").isNotEmpty() && snapshot.roles.isNotEmpty())
            assertTrue(system.diff(snapshot).toString(), system.diff(snapshot).none { SystemBackup.isCommon(it) })
            root.run("settings put --user 0 system font_scale 1.3").orThrow()
            val item = system.diff(snapshot).single { it is SystemItem.Setting && it.key == "font_scale" }
            system.apply(item)
            assertEquals("1.15", root.run("settings get --user 0 system font_scale").out)
        } finally {
            if (original == "null") root.run("settings delete --user 0 system font_scale")
            else root.run("settings put --user 0 system font_scale $original")
        }
    }
}
