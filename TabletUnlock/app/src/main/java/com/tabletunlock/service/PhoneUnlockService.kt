package com.tabletunlock.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tabletunlock.net.PhoneListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * خدمة المقدمة في الجوال: تُبقي الاستماع يعمل حتى لو أُغلق التطبيق أو قُفلت الشاشة،
 * فيظهر إشعار طلب الفتح على شاشة القفل.
 */
class PhoneUnlockService : Service() {
    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var listener: PhoneListener

    override fun onCreate() {
        super.onCreate()
        listener = PhoneListener(this)
        val notification = ServiceNotifications.build(
            this,
            "جاهز لاستقبال طلبات الفتح",
            "التطبيق يستمع في الخلفية.",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ServiceNotifications.PHONE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ServiceNotifications.PHONE_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ListeningState.running.value) {
            ListeningState.running.value = true
            listener.start(scope) { line -> ListeningState.appendLog(line) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        listener.stop()
        scope.cancel()
        ListeningState.running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PhoneUnlockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneUnlockService::class.java))
        }
    }
}
