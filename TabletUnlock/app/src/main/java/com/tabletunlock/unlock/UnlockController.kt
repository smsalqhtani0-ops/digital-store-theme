package com.tabletunlock.unlock

import android.app.KeyguardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Display
import com.tabletunlock.data.UnlockMethod
import com.tabletunlock.shizuku.ShellResult
import com.tabletunlock.shizuku.ShellService.Companion.INPUT
import com.tabletunlock.shizuku.ShellService.Companion.WM
import com.tabletunlock.shizuku.ShizukuManager
import kotlinx.coroutines.delay

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object AlreadyUnlocked : UnlockResult
    data object StillLocked : UnlockResult
    data class Failed(val step: String, val error: String) : UnlockResult
}

/** ينفّذ تسلسل الفتح عبر Shizuku. الرسائل المرسلة إلى onStep لا تحتوي الرمز أبداً. */
class UnlockController(private val context: Context) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    val isLocked: Boolean get() = keyguard.isKeyguardLocked
    val isScreenOn: Boolean get() = power.isInteractive

    suspend fun lockScreen(): ShellResult = ShizukuManager.exec(INPUT, "keyevent", KEY_SLEEP)

    suspend fun unlock(pin: CharArray, method: UnlockMethod, onStep: (String) -> Unit): UnlockResult {
        onStep("إيقاظ الشاشة (keyevent 224)")
        ShizukuManager.exec(INPUT, "keyevent", KEY_WAKEUP).failOr("إيقاظ الشاشة")?.let { return it }
        delay(600)

        if (!keyguard.isKeyguardLocked) return UnlockResult.AlreadyUnlocked

        when (method) {
            UnlockMethod.SWIPE -> {
                val (width, height) = screenSize()
                val x = (width / 2).toString()
                val fromY = (height * 0.85).toInt().toString()
                val toY = (height * 0.25).toInt().toString()
                onStep("إظهار لوحة الرمز (swipe ${width}x$height)")
                ShizukuManager.exec(INPUT, "swipe", x, fromY, x, toY, SWIPE_DURATION_MS)
                    .failOr("السحب")?.let { return it }
            }
            UnlockMethod.DISMISS_KEYGUARD -> {
                onStep("إظهار لوحة الرمز (wm dismiss-keyguard)")
                ShizukuManager.exec(WM, "dismiss-keyguard").failOr("dismiss-keyguard")?.let { return it }
            }
        }
        delay(800)

        onStep("إدخال الرمز")
        ShizukuManager.exec(INPUT, "text", String(pin)).failOr("إدخال الرمز")?.let { return it }
        delay(300)

        onStep("تأكيد (keyevent 66)")
        ShizukuManager.exec(INPUT, "keyevent", KEY_ENTER).failOr("التأكيد")?.let { return it }
        delay(1200)

        return if (keyguard.isKeyguardLocked) UnlockResult.StillLocked else UnlockResult.Success
    }

    private fun ShellResult.failOr(step: String): UnlockResult? =
        if (ok) null else UnlockResult.Failed(step, error.ifBlank { "exit=$exitCode" })

    private fun screenSize(): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private companion object {
        const val KEY_SLEEP = "223"
        const val KEY_WAKEUP = "224"
        const val KEY_ENTER = "66"
        const val SWIPE_DURATION_MS = "250"
    }
}
