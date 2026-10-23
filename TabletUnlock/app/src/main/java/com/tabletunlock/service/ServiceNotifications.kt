package com.tabletunlock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
}
