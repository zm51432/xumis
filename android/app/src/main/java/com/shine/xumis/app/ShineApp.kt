package com.shine.xumis.app

import android.app.Application
import com.shine.xumis.app.util.ServerConfig

class ShineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerConfig.init(this)
    }
}
