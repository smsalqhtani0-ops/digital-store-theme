package com.tabletunlock.biometric

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tabletunlock.R

/**
 * إشعار ملء الشاشة لبدء الفتح من الجوال (بلاطة الإعدادات).
 * يظهر فوق شاشة القفل ويفتح شاشة البصمة، لأن البلاطة لا تستطيع فتح نافذة فوق القفل مباشرة.
 */
object InitiateNotifier {
    const val NOTIFICATION_ID = 43

    fun show(context: Context) {
        UnlockNotifier.ensureChannel(context)
        val intent = Intent(context, PhoneInitiateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 1, intent, flags)

        val notification = NotificationCompat.Builder(context, UnlockNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(R.string.notif_initiate_title))
            .setContentText(context.getString(R.string.notif_initiate_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
