package com.aybrkaknc.easyandroid.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Removal {
    @SerialName("Recommended") RECOMMENDED,
    @SerialName("Advanced") ADVANCED,
    @SerialName("Expert") EXPERT,
    @SerialName("Unsafe") UNSAFE,
}

@Serializable
data class DebloatInfo(
    val list: String,
    val description: String,
    val removal: Removal,
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
)

@Serializable
enum class DebloatAction {
    @SerialName("disable") DISABLE,
    @SerialName("enable") ENABLE,
    @SerialName("uninstall") UNINSTALL,
    @SerialName("restore") RESTORE;

    fun command(packageName: String): String = when (this) {
        DISABLE -> "pm disable-user --user 0 $packageName"
        ENABLE -> "pm enable --user 0 $packageName"
        UNINSTALL -> "pm uninstall -k --user 0 $packageName"
        RESTORE -> "cmd package install-existing --user 0 $packageName"
    }

    val removes get() = this == DISABLE || this == UNINSTALL
}

@Serializable
data class ProfileEntry(@SerialName("package") val packageName: String, val action: DebloatAction)

@Serializable
data class DebloatProfile(
    val format: String,
    val version: Int,
    val name: String,
    val createdAt: String,
    val entries: List<ProfileEntry>,
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "easyadb-debloat-profile"

        fun parse(text: String): DebloatProfile {
            val profile = try {
                JSON.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                throw AppException(ErrorCode.BACKUP_INVALID, e.message.orEmpty())
            }
            if (profile.format != FORMAT || profile.version != 1) {
                throw AppException(ErrorCode.BACKUP_INVALID, profile.format)
            }
            profile.entries.forEach { Sh.checkPackage(it.packageName) }
            return profile
        }
    }
}

class Debloat(private val shell: Root, private val database: Map<String, DebloatInfo>, private val ownPackage: String) {
    /** Packages the app refuses to disable or uninstall, whatever asked for it. */
    fun isProtected(packageName: String): Boolean =
        packageName == ownPackage || !Sh.isPackage(packageName) || packageName in ALWAYS ||
            database[packageName]?.removal == Removal.UNSAFE

    fun apply(entry: ProfileEntry) {
        Sh.checkPackage(entry.packageName)
        if (entry.action.removes && isProtected(entry.packageName)) {
            throw AppException(ErrorCode.PROTECTED, entry.packageName)
        }
        val result = shell.run(entry.action.command(entry.packageName))
        val text = (result.out + "\n" + result.err).trim()
        val failed = listOf("failure", "exception", "error:").any { it in text.lowercase() }
        if (!result.ok || failed) throw AppException(ErrorCode.COMMAND_FAILED, text)
    }

    companion object {
        private val ALWAYS = setOf("com.android.systemui", "com.android.settings", "com.android.shell")

        fun parseDatabase(text: String): Map<String, DebloatInfo> = JSON.decodeFromString(text)
    }
}
