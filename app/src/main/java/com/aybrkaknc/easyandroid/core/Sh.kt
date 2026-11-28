package com.aybrkaknc.easyandroid.core

enum class ErrorCode {
    NO_ROOT, INVALID_INPUT, NOT_FOUND, NO_SPACE, BACKUP_INVALID, INTEGRITY, INSTALL_FAILED,
    COMMAND_FAILED, PROTECTED, CANCELLED, UNSUPPORTED,

    /** EasyADB is not looking at this phone, or the PC was lost, refused or failed. */
    PC_MISSING, PC_LOST, PC_REJECTED, PC_FAILED,

    /** Nobody answered on the PC, or another job for the PC runs here. */
    PC_EXPIRED, PC_BUSY, PC_STOPPED,
}

class AppException(val code: ErrorCode, val detail: String = "") : Exception("$code: $detail")

/** Everything that ends up in a root shell goes through here. */
object Sh {
    private val SAFE = Regex("[A-Za-z0-9\\-_./:=@%+,]+")
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val TOKEN = Regex("[A-Za-z0-9._]{1,199}")

    fun quote(arg: String): String =
        if (SAFE.matches(arg)) arg else "'" + arg.replace("'", "'\\''") + "'"

    fun join(vararg args: String): String = args.joinToString(" ") { quote(it) }

    fun isPackage(name: String): Boolean = PACKAGE.matches(name)

    fun checkPackage(name: String): String =
        if (isPackage(name)) name else throw AppException(ErrorCode.INVALID_INPUT, name)

    /** Permission, app op, role and setting names. */
    fun isToken(name: String): Boolean = TOKEN.matches(name)

    fun checkPath(path: String): String {
        val bad = !path.startsWith("/") || path.trim('/').isEmpty() ||
            path.split('/').any { it == ".." } || path.any { it.isISOControl() }
        if (bad) throw AppException(ErrorCode.INVALID_INPUT, path)
        return path.trimEnd('/')
    }

    /** A single folder or file name, never a path. */
    fun checkName(name: String): String {
        val bad = name.isEmpty() || name == "." || name == ".." ||
            name.any { it == '/' || it == '\\' || it.isISOControl() }
        if (bad) throw AppException(ErrorCode.INVALID_INPUT, name)
        return name
    }

    /** A relative path inside a backup folder. */
    fun checkRelative(path: String): String {
        val bad = path.isEmpty() || path.contains('\\') || path.contains(':') || path.any { it.isISOControl() } ||
            path.split('/').any { it.isEmpty() || it == "." || it == ".." }
        if (bad) throw AppException(ErrorCode.INVALID_INPUT, path)
        return path
    }
}
