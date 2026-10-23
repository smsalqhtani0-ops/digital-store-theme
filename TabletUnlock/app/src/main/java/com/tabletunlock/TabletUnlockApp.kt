package com.tabletunlock

import android.app.Application
import com.tabletunlock.shizuku.ShizukuManager

class TabletUnlockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
    }
}
