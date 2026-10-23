package com.tabletunlock

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs
import com.tabletunlock.data.PairingStore
import com.tabletunlock.data.UnlockMethod
import com.tabletunlock.net.TabletClient
import com.tabletunlock.pairing.TabletPairing
import com.tabletunlock.security.PinStore
import com.tabletunlock.shizuku.ShizukuManager
import com.tabletunlock.shizuku.ShizukuState
import com.tabletunlock.unlock.UnlockController
import com.tabletunlock.unlock.UnlockResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TabletUiState(
    val hasPin: Boolean = false,
    val method: UnlockMethod = UnlockMethod.SWIPE,
    val busy: Boolean = false,
    val countdown: Int = 0,
    val log: List<String> = emptyList(),
    // المرحلة 2: الاقتران والاتصال
    val paired: Boolean = false,
    val pairedPhoneName: String? = null,
    val qrContent: String? = null,
    val connBusy: Boolean = false,
    val connLog: List<String> = emptyList(),
)

class TabletViewModel(app: Application) : AndroidViewModel(app) {
    private val pinStore = PinStore(app)
    private val prefs = AppPrefs(app)
    private val controller = UnlockController(app)
    private val power = app.getSystemService(PowerManager::class.java)
    private val pairingStore = PairingStore(app, AppMode.TABLET)
    private val pairing = TabletPairing(app)
    private val client = TabletClient(app)

    val shizukuState: StateFlow<ShizukuState> = ShizukuManager.state

    private val _ui = MutableStateFlow(
        TabletUiState(
            hasPin = pinStore.hasPin(),
            method = prefs.unlockMethod,
            paired = pairingStore.paired,
            pairedPhoneName = pairingStore.peerServiceName,
        ),
    )
    val ui: StateFlow<TabletUiState> = _ui.asStateFlow()

    fun refreshShizuku() = ShizukuManager.refresh()
    fun requestShizukuPermission() = ShizukuManager.requestPermission()

    fun savePin(pin: CharArray): Boolean {
        if (!PinStore.isValid(String(pin))) return false
        pinStore.save(pin)
        _ui.update { it.copy(hasPin = true) }
        return true
    }

    fun clearPin() {
        pinStore.clear()
        _ui.update { it.copy(hasPin = false) }
    }

    fun setMethod(method: UnlockMethod) {
        prefs.unlockMethod = method
        _ui.update { it.copy(method = method) }
    }

    fun startPairing() {
        viewModelScope.launch {
            when (val result = pairing.begin(
                onPaired = { phoneName ->
                    _ui.update {
                        it.copy(paired = true, pairedPhoneName = phoneName, qrContent = null)
                    }
                },
                onFailure = { message ->
                    _ui.update { it.copy(qrContent = null, connLog = it.connLog + message) }
                },
            )) {
                is TabletPairing.Result.Ready ->
                    _ui.update { it.copy(qrContent = result.payload.encode()) }
                is TabletPairing.Result.Error ->
                    _ui.update { it.copy(connLog = it.connLog + result.message) }
            }
        }
    }

    fun cancelPairing() = _ui.update { it.copy(qrContent = null) }

    fun unpair() {
        pairingStore.clear()
        _ui.update { it.copy(paired = false, pairedPhoneName = null, qrContent = null) }
    }

    fun testConnection() {
        if (_ui.value.connBusy) return
        _ui.update { it.copy(connBusy = true, connLog = emptyList()) }
        viewModelScope.launch {
            val outcome = client.testConnection { line ->
                _ui.update { it.copy(connLog = it.connLog + line) }
            }
            val message = when (outcome) {
                TabletClient.Outcome.Success -> "✅ الاتصال والتوثيق ناجح. الجهازان يريان بعضهما."
                is TabletClient.Outcome.Failed -> "❌ ${outcome.message}"
            }
            _ui.update { it.copy(connBusy = false, connLog = it.connLog + message) }
        }
    }

    fun runTest() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, log = emptyList()) }

        viewModelScope.launch {
            val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TabletUnlock:test")
            wakeLock.acquire(60_000)
            var pin: CharArray? = null
            try {
                pin = pinStore.load()
                if (pin == null) {
                    log("تعذّر قراءة الرمز المحفوظ. احذفه وأدخله من جديد.")
                    return@launch
                }

                log("قفل الشاشة (keyevent 223)…")
                val lock = controller.lockScreen()
                if (!lock.ok) {
                    log("فشل القفل: ${lock.error.ifBlank { "exit=${lock.exitCode}" }}")
                    return@launch
                }

                for (seconds in 5 downTo 1) {
                    _ui.update { it.copy(countdown = seconds) }
                    delay(1000)
                }
                _ui.update { it.copy(countdown = 0) }

                log(if (controller.isScreenOn) "تنبيه: الشاشة ما زالت تعمل." else "الشاشة مطفأة ✓")
                if (!controller.isLocked) {
                    log("تنبيه: الجهاز غير مقفل بعد. فعّل \"القفل الفوري بزر التشغيل\" في إعدادات الأمان.")
                }

                val result = controller.unlock(pin, _ui.value.method) { log(it) }
                log(
                    when (result) {
                        UnlockResult.Success -> "✅ نجح الفتح."
                        UnlockResult.AlreadyUnlocked -> "الجهاز لم يكن مقفلاً، فلم يُدخَل الرمز."
                        UnlockResult.StillLocked ->
                            "❌ أُرسلت الأوامر لكن الجهاز ما زال مقفلاً. جرّب الطريقة الأخرى لإظهار لوحة الرمز."
                        is UnlockResult.Failed -> "❌ فشلت خطوة \"${result.step}\": ${result.error}"
                    },
                )
            } finally {
                pin?.fill('\u0000')
                if (wakeLock.isHeld) wakeLock.release()
                _ui.update { it.copy(busy = false, countdown = 0) }
            }
        }
    }

    private fun log(line: String) {
        _ui.update { it.copy(log = it.log + line) }
    }
}
