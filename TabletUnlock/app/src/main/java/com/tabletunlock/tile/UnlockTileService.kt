package com.tabletunlock.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tabletunlock.biometric.InitiateNotifier
import com.tabletunlock.biometric.PhoneInitiateActivity

/** بلاطة "فتح التابلت": بصمة ← إرسال أمر الفتح. تعمل من شاشة القفل عبر إشعار ملء الشاشة. */
class UnlockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            icon = Icon.createWithResource(this@UnlockTileService, android.R.drawable.ic_lock_idle_lock)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            // لا يمكن فتح نافذة فوق القفل من البلاطة مباشرة؛ نستخدم إشعار ملء الشاشة.
            InitiateNotifier.show(applicationContext)
            return
        }
        val intent = Intent(this, PhoneInitiateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
