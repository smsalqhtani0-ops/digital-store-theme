package com.tabletunlock.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tabletunlock.unlock.AutoUnlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * خدمة المقدمة في التابلت: تستمع لحدث إضاءة الشاشة وترسل طلب الفتح تلقائياً.
 * يجب تسجيل مستقبِل ACTION_SCREEN_ON في وقت التشغيل (لا يعمل من AndroidManifest).
 */
class TabletAutoService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var unlocker: AutoUnlocker

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                scope.launch { unlocker.attempt { setStatus(it) } }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        unlocker = AutoUnlocker(this)
        val notification = ServiceNotifications.build(
            this,
            "التشغيل التلقائي مُفعّل",
            "ينتظر إضاءة الشاشة لطلب الفتح.",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ServiceNotifications.TABLET_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ServiceNotifications.TABLET_ID, notification)
        }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        AutoState.running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun setStatus(status: String) {
        AutoState.lastStatus.value = status
        ServiceNotifications.update(this, ServiceNotifications.TABLET_ID, "التشغيل التلقائي", status)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        AutoState.running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, TabletAutoService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TabletAutoService::class.java))
        }
    }
}
