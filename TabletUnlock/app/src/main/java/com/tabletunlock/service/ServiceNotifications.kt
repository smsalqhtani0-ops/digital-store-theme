package com.tabletunlock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** الإشعار الدائم الذي يُبقي الخدمة في المقدمة (أولوية منخفضة، غير مزعج). */
object ServiceNotifications {
    const val CHANNEL_ID = "service_status"
    const val PHONE_ID = 101
    const val TABLET_ID = 102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "حالة الخدمة", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, title: String, text: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun update(context: Context, id: Int, title: String, text: String) {
        context.getSystemService(NotificationManager::class.java).notify(id, build(context, title, text))
    }

    /** إشعار الجوال الدائم مع زر "فتح التابلت" يظهر على شاشة القفل. */
    fun phoneNotification(context: Context): Notification {
        ensureChannel(context)
        val intent = Intent(context, com.tabletunlock.biometric.PhoneInitiateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(com.tabletunlock.R.string.phone_notif_title))
            .setContentText(context.getString(com.tabletunlock.R.string.phone_notif_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_lock_idle_lock, context.getString(com.tabletunlock.R.string.phone_notif_action), pending)
            .build()
    }
}
