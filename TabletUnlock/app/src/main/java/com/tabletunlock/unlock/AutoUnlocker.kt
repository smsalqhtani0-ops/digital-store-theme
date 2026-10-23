package com.tabletunlock.unlock

import android.app.KeyguardManager
import android.content.Context
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs
import com.tabletunlock.data.PairingStore
import com.tabletunlock.net.TabletClient
import com.tabletunlock.security.PinStore
import com.tabletunlock.shizuku.ShizukuManager
import com.tabletunlock.shizuku.ShizukuState
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * الفتح التلقائي بلا واجهة: عند إضاءة الشاشة والجهاز مقفل، يطلب الفتح من الجوال،
 * وعند موافقة البصمة يُدخل الرمز مباشرة (بلا قفل أو عدّ تنازلي).
 * يعالج: غياب الاقتران/الرمز، توقف Shizuku، تعذّر الوصول للجوال.
 */
class AutoUnlocker(context: Context) {
    private val appContext = context.applicationContext
    private val keyguard = appContext.getSystemService(KeyguardManager::class.java)
    private val prefs = AppPrefs(appContext)
    private val pinStore = PinStore(appContext)
    private val pairing = PairingStore(appContext, AppMode.TABLET)
    private val controller = UnlockController(appContext)
    private val client = TabletClient(appContext)

    suspend fun attempt(onStatus: (String) -> Unit) {
        if (mutex.isLocked) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastAttempt < COOLDOWN_MS) return
            lastAttempt = now

            // بعض أجهزة أونر تتأخّر في ضبط حالة القفل بعد إضاءة الشاشة؛ ننتظر قليلاً ونعيد الفحص.
            if (!keyguard.isKeyguardLocked) {
                delay(700)
                if (!keyguard.isKeyguardLocked) return
            }
            if (!pairing.paired) {
                onStatus("لم يتم الاقتران مع الجوال.")
                return
            }
            if (!pinStore.hasPin()) {
                onStatus("لا يوجد رمز محفوظ.")
                return
            }
            if (ShizukuManager.state.value != ShizukuState.READY) {
                onStatus("Shizuku متوقف. شغّله من تطبيق Shizuku.")
                return
            }

            onStatus("إرسال طلب الفتح للجوال…")
            when (val outcome = client.requestUnlock { onStatus(it) }) {
                TabletClient.UnlockOutcome.Approved -> performUnlock(onStatus)
                is TabletClient.UnlockOutcome.Rejected -> onStatus("رُفض: ${outcome.message}")
                is TabletClient.UnlockOutcome.Failed -> onStatus("تعذّر: ${outcome.message}")
            }
        }
    }

    private suspend fun performUnlock(onStatus: (String) -> Unit) {
        var pin: CharArray? = null
        try {
            pin = pinStore.load()
            if (pin == null) {
                onStatus("تعذّر قراءة الرمز المحفوظ.")
                return
            }
            onStatus("موافقة البصمة ✓ — جارٍ الفتح…")
            val result = controller.unlock(pin, prefs.unlockMethod) { }
            onStatus(
                when (result) {
                    UnlockResult.Success -> "✅ فُتح التابلت."
                    UnlockResult.AlreadyUnlocked -> "الجهاز مفتوح أصلاً."
                    UnlockResult.StillLocked -> "❌ لم يُفتح. جرّب الطريقة الأخرى لإظهار لوحة الرمز."
                    is UnlockResult.Failed -> "❌ فشلت خطوة \"${result.step}\": ${result.error}"
                },
            )
        } finally {
            pin?.fill('\u0000')
        }
    }

    private companion object {
        val mutex = Mutex()

        @Volatile
        var lastAttempt = 0L
        const val COOLDOWN_MS = 5_000L
    }
}
