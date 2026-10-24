package com.tabletunlock.widget

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tabletunlock.biometric.InitiateNotifier
import com.tabletunlock.biometric.PhoneInitiateActivity

/**
 * نقطة بدء الفتح من الويدجت. المستقبِل يعمل حتى من شاشة القفل دون تأجيل:
 * - إذا كانت الشاشة مقفلة: يعرض إشعار ملء الشاشة الذي يفتح شاشة البصمة فوق القفل.
 * - إذا كانت مفتوحة: يفتح شاشة البصمة مباشرة.
 */
class UnlockTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            InitiateNotifier.show(context)
        } else {
            context.startActivity(
                Intent(context, PhoneInitiateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    companion object {
        const val ACTION_TRIGGER = "com.tabletunlock.action.TRIGGER_UNLOCK"
    }
}
