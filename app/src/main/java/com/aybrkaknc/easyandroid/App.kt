package com.aybrkaknc.easyandroid

import android.app.Application
import com.aybrkaknc.easyandroid.core.BackupEngine
import com.aybrkaknc.easyandroid.core.BackupStore
import com.aybrkaknc.easyandroid.core.Debloat
import com.aybrkaknc.easyandroid.core.DebloatInfo
import com.aybrkaknc.easyandroid.core.RestoreEngine
import com.aybrkaknc.easyandroid.core.SuRoot
import com.aybrkaknc.easyandroid.core.SystemBackup
import com.topjohnwu.superuser.Shell

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(30))
        Env.app = this
        Env.prefs = Prefs(this)
    }
}

object Env {
    lateinit var app: App
    lateinit var prefs: Prefs

    val database: Map<String, DebloatInfo> by lazy {
        Debloat.parseDatabase(app.assets.open("debloat_db.json").bufferedReader().use { it.readText() })
    }

    // Built per use, because the backup folder can change.
    fun store() = BackupStore(SuRoot, prefs.state.value.backupRoot)
    fun backups() = BackupEngine(SuRoot, store(), BuildConfig.VERSION_NAME)
    fun restores() = RestoreEngine(SuRoot, store())
    fun system() = SystemBackup(SuRoot, store())
    fun debloat() = Debloat(SuRoot, database, app.packageName)
}
