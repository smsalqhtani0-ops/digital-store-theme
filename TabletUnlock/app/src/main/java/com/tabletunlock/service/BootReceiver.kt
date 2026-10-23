package com.tabletunlock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs

/** يعيد تشغيل الخدمات بعد إقلاع الجهاز إن كانت مفعّلة. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = AppPrefs(context)
        when (prefs.mode) {
            AppMode.PHONE -> if (prefs.listeningEnabled) PhoneUnlockService.start(context)
            AppMode.TABLET -> if (prefs.autoUnlockEnabled || prefs.receiveFromPhoneEnabled) TabletAutoService.start(context)
            null -> Unit
        }
    }
}
