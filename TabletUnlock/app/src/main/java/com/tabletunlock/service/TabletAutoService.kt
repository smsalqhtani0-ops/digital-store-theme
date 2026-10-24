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
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs
import com.tabletunlock.data.PairingStore
import com.tabletunlock.data.TabletLockMode
import com.tabletunlock.lock.AppLockActivity
import com.tabletunlock.net.TabletRequestHandler
import com.tabletunlock.net.TransportListener
import com.tabletunlock.unlock.AutoUnlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * خدمة المقدمة في التابلت:
 * - الفتح التلقائي: تستمع لإضاءة الشاشة وترسل طلب فتح للجوال.
 * - الاستقبال من الجوال: تشغّل مستمعاً (واي فاي + بلوتوث) لأوامر الفتح من بلاطة الجوال.
 * يعمل أيٌّ منهما حسب الإعدادات.
 */
class TabletAutoService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: AppPrefs
    private lateinit var unlocker: AutoUnlocker
    private var listener: TransportListener? = null
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            if (prefs.lockMode == TabletLockMode.APP_LOCK) {
                context.startActivity(
                    Intent(context, AppLockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            } else {
                scope.launch { unlocker.attempt { setStatus(it) } }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        unlocker = AutoUnlocker(this)
        startInForeground()

        if (prefs.autoUnlockEnabled || prefs.lockMode == TabletLockMode.APP_LOCK) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
            receiverRegistered = true
        }
        if (prefs.receiveFromPhoneEnabled || prefs.lockMode == TabletLockMode.APP_LOCK) {
            val serviceName = PairingStore(this, AppMode.TABLET).ownServiceName()
            listener = TransportListener(this, serviceName, TabletRequestHandler(this)).also {
                it.start(scope) { line -> setStatus(line) }
            }
        }
        AutoState.running.value = prefs.autoUnlockEnabled
    }

    private fun startInForeground() {
        val notification = ServiceNotifications.build(this, "خدمة التابلت تعمل", "جاهزة للفتح التلقائي/الاستقبال.")
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun setStatus(status: String) {
        AutoState.lastStatus.value = status
        ServiceNotifications.update(this, ServiceNotifications.TABLET_ID, "خدمة التابلت", status)
    }

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        listener?.stop()
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

        /** يعيد ضبط الخدمة حسب الإعدادات: يشغّلها إن لزم، ويعيد تشغيلها لتلتقط التغيير. */
        fun reconcile(context: Context) {
            val prefs = AppPrefs(context)
            stop(context)
            if (prefs.autoUnlockEnabled || prefs.receiveFromPhoneEnabled ||
                prefs.lockMode == TabletLockMode.APP_LOCK
            ) {
                start(context)
            }
        }
    }
}
