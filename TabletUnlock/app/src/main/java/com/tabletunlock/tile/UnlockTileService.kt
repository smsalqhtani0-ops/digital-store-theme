package com.tabletunlock.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.tabletunlock.biometric.PhoneInitiateActivity

/** بلاطة الإعدادات السريعة "فتح التابلت": تفتح شاشة البصمة ثم ترسل الأمر. */
class UnlockTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, PhoneInitiateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
