package com.tabletunlock.lock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tabletunlock.net.TabletClient
import com.tabletunlock.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * شاشة قفل التطبيق (بدون Shizuku). تغطّي الجهاز، وتُرفع فقط عند موافقة البصمة من الجوال.
 * ملاحظة أمان: هذا قفل تطبيق وليس قفل نظام؛ للاستخدام المنزلي.
 */
class AppLockActivity : ComponentActivity() {
    private val client by lazy { TabletClient(this) }
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverEverything()

        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier.size(96.dp).clip(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🔒", style = MaterialTheme.typography.displayMedium)
                            }
                            Text("التابلت مقفل", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "اطلب الفتح من جوالك، أو افتح من الجوال مباشرة.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = ::requestUnlock, enabled = !busy) {
                                Text(if (busy) "جارٍ الطلب…" else "فتح عبر الجوال")
                            }
                            if (status.isNotBlank()) {
                                Text(
                                    status,
                                    color = if (status.contains("❌")) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // يُرفع القفل عند إشارة الفتح من الجوال (البلاطة/الويدجت).
        lifecycleScope.launch {
            AppLockState.unlockSignal.collect { finish() }
        }
    }

    private fun requestUnlock() {
        if (busy) return
        busy = true
        status = ""
        lifecycleScope.launch {
            val outcome = client.requestUnlock { }
            busy = false
            when (outcome) {
                TabletClient.UnlockOutcome.Approved -> finish()
                is TabletClient.UnlockOutcome.Rejected -> status = "❌ ${outcome.message}"
                is TabletClient.UnlockOutcome.Failed -> status = "❌ ${outcome.message}"
            }
        }
    }

    // حارس بسيط: إعادة إظهار القفل عند محاولة المغادرة (ليس بقوة وضع الكشك).
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishing) {
            startActivity(intent)
        }
    }

    private fun showOverEverything() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
