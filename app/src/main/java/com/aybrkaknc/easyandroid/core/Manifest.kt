package com.aybrkaknc.easyandroid.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val MANIFEST = "manifest.json"

/** v1 is EasyADB's format. v2 only differs in that the archives are zstd compressed. */
const val FORMAT_PLAIN = 1
const val FORMAT_ZSTD = 2

@Serializable
enum class FileKind {
    @SerialName("apk") APK,
    @SerialName("obb") OBB,
    @SerialName("data") DATA,
}

@Serializable
data class BackupFile(
    val kind: FileKind,
    /** Path inside the backup folder, using `/`. */
    val stored: String,
    /** Original file name on the device. */
    val name: String,
    val size: Long,
    val sha256: String,
)

/**
 * Archives EasyADB does not know. Its parser rejects unknown file kinds but ignores unknown
 * manifest fields, so they live outside `files`.
 */
@Serializable
enum class ExtraKind {
    @SerialName("device_data") DEVICE_DATA,
    @SerialName("external_data") EXTERNAL_DATA,
}

@Serializable
data class ExtraFile(val kind: ExtraKind, val stored: String, val size: Long, val sha256: String)

@Serializable
data class DeviceSnapshot(
    val model: String = "",
    val serial: String = "",
    val androidVersion: String = "",
    val sdk: Int = 0,
)

@Serializable
data class PermissionGrant(val name: String, val granted: Boolean)

@Serializable
data class AppOp(val op: String, val mode: String)

@Serializable
data class Manifest(
    val formatVersion: Int,
    val appVersion: String,
    @SerialName("package") val packageName: String,
    val label: String? = null,
    val versionName: String? = null,
    val createdAt: String,
    val device: DeviceSnapshot,
    val files: List<BackupFile>,
    val permissions: List<PermissionGrant> = emptyList(),
    val extras: List<ExtraFile> = emptyList(),
    val appOps: List<AppOp> = emptyList(),
    val batteryExempt: Boolean = false,
) {
    val compressed get() = formatVersion == FORMAT_ZSTD
    val totalBytes get() = files.sumOf { it.size } + extras.sumOf { it.size }

    fun has(kind: FileKind) = files.any { it.kind == kind }

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        fun parse(text: String): Manifest {
            val manifest = try {
                JSON.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw AppException(ErrorCode.BACKUP_INVALID, e.message.orEmpty())
            } catch (e: IllegalArgumentException) {
                throw AppException(ErrorCode.BACKUP_INVALID, e.message.orEmpty())
            }
            if (manifest.formatVersion != FORMAT_PLAIN && manifest.formatVersion != FORMAT_ZSTD) {
                throw AppException(ErrorCode.BACKUP_INVALID, "format ${manifest.formatVersion}")
            }
            Sh.checkPackage(manifest.packageName)
            manifest.files.forEach {
                Sh.checkRelative(it.stored)
                Sh.checkName(it.name)
            }
            manifest.extras.forEach { Sh.checkRelative(it.stored) }
            return manifest
        }
    }
}

val JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun folderName(packageName: String, time: OffsetDateTime): String =
    packageName + "_" + time.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

fun timestamp(time: OffsetDateTime): String =
    time.truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun parseTimestamp(text: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(text) }.getOrNull()
