package com.tabletunlock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs
import com.tabletunlock.ui.ModeSelectScreen
import com.tabletunlock.ui.PhoneScreen
import com.tabletunlock.ui.TabletScreen
import com.tabletunlock.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // يمنع لقطات الشاشة وظهور المحتوى في قائمة التطبيقات الأخيرة (فيها حقل الرمز).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            AppTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context.applicationContext) }
    var mode by remember { mutableStateOf(prefs.mode) }

    fun select(newMode: AppMode?) {
        prefs.mode = newMode
        mode = newMode
    }

    when (mode) {
        null -> ModeSelectScreen(onSelect = ::select)
        AppMode.TABLET -> TabletScreen(onChangeMode = { select(null) })
        AppMode.PHONE -> PhoneScreen(onChangeMode = { select(null) })
    }
}
