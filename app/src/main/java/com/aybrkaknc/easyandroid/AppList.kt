package com.aybrkaknc.easyandroid

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.aybrkaknc.easyandroid.core.DebloatInfo
import java.io.File

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val system: Boolean,
    val installed: Boolean,
    val enabled: Boolean,
    val apkBytes: Long,
    val debloat: DebloatInfo?,
    val installTime: Long = 0,
    val updateTime: Long = 0,
)

/** Every package known to the system, including system apps removed for this user. */
fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
    return packages.mapNotNull { info ->
        val app = info.applicationInfo ?: return@mapNotNull null
        val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val installed = app.flags and ApplicationInfo.FLAG_INSTALLED != 0
        // Leftovers of removed user apps have nothing to back up or restore.
        if (!system && (!installed || !File(app.sourceDir.orEmpty()).exists())) return@mapNotNull null
        AppInfo(
            packageName = info.packageName,
            label = app.loadLabel(pm).toString(),
            versionName = info.versionName.orEmpty(),
            system = system,
            installed = installed,
            enabled = app.enabled,
            apkBytes = (listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()).sumOf { File(it).length() },
            debloat = Env.database[info.packageName],
            installTime = info.firstInstallTime,
            updateTime = info.lastUpdateTime,
        )
    }.sortedBy { it.label.lowercase() }
}
