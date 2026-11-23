package com.aybrkaknc.easyandroid.core

/**
 * Real path behind a folder picked with the system file picker. Root commands need a path, so
 * only internal storage and SD cards work; cloud providers have none.
 */
fun treePath(authority: String?, documentId: String): String? {
    if (authority != "com.android.externalstorage.documents") return null
    val volume = documentId.substringBefore(':', "")
    val folder = documentId.substringAfter(':').trim('/')
    val base = when {
        volume == "primary" -> "/storage/emulated/0"
        volume == "home" -> "/storage/emulated/0/Documents"
        Regex("[A-Za-z0-9-]+").matches(volume) -> "/storage/$volume"
        else -> return null
    }
    val path = if (folder.isEmpty()) base else "$base/$folder"
    return runCatching { Sh.checkPath(path) }.getOrNull()
}
