package com.shine.xumis.app

import android.app.Application
import com.shine.xumis.app.util.AppPrefs
import com.shine.xumis.app.util.CrashGuard
import com.shine.xumis.app.util.HealthLog
import com.shine.xumis.app.util.ServerConfig

class ShineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        ServerConfig.purgeLegacy(this)
        CrashGuard.install(this)
        HealthLog.firstLaunch(this)
    }
}
