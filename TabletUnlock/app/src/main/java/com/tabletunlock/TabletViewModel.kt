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
import com.tabletunlock.service.AutoState
import com.tabletunlock.service.TabletAutoService
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
    // المرحلة 3: الفتح من الجوال بالبصمة
    val remoteBusy: Boolean = false,
    val remoteLog: List<String> = emptyList(),
    val receiveEnabled: Boolean = false,
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
    val autoRunning: StateFlow<Boolean> = AutoState.running
    val autoStatus: StateFlow<String> = AutoState.lastStatus

    private val _ui = MutableStateFlow(
        TabletUiState(
            hasPin = pinStore.hasPin(),
            method = prefs.unlockMethod,
            paired = pairingStore.paired,
            pairedPhoneName = pairingStore.peerServiceName,
            receiveEnabled = prefs.receiveFromPhoneEnabled,
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
        if (_ui.value.busy || _ui.value.remoteBusy) return
        _ui.update { it.copy(busy = true, log = emptyList()) }
        viewModelScope.launch {
            performUnlockSequence { line -> log(line) }
            _ui.update { it.copy(busy = false, countdown = 0) }
        }
    }

    fun requestRemoteUnlock() {
        if (_ui.value.busy || _ui.value.remoteBusy) return
        _ui.update { it.copy(remoteBusy = true, remoteLog = emptyList()) }
        viewModelScope.launch {
            val outcome = client.requestUnlock { line -> remoteLog(line) }
            when (outcome) {
                TabletClient.UnlockOutcome.Approved -> {
                    remoteLog("✅ وافق الجوال بالبصمة. يُنفَّذ الفتح الآن…")
                    performUnlockSequence { line -> remoteLog(line) }
                }
                is TabletClient.UnlockOutcome.Rejected -> remoteLog("❌ ${outcome.message}")
                is TabletClient.UnlockOutcome.Failed -> remoteLog("❌ ${outcome.message}")
            }
            _ui.update { it.copy(remoteBusy = false, countdown = 0) }
        }
    }

    fun setAuto(enabled: Boolean) {
        prefs.autoUnlockEnabled = enabled
        _ui.update { it.copy(receiveEnabled = prefs.receiveFromPhoneEnabled) }
        TabletAutoService.reconcile(getApplication())
    }

    fun setReceive(enabled: Boolean) {
        prefs.receiveFromPhoneEnabled = enabled
        _ui.update { it.copy(receiveEnabled = enabled) }
        TabletAutoService.reconcile(getApplication())
    }

    /** يقفل الشاشة، ينتظر 5 ثوانٍ، ثم يفتح بالرمز المحفوظ. يُستخدم للاختبار المحلي والفتح من الجوال. */
    private suspend fun performUnlockSequence(onLog: (String) -> Unit) {
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TabletUnlock:unlock")
        wakeLock.acquire(60_000)
        var pin: CharArray? = null
        try {
            pin = pinStore.load()
            if (pin == null) {
                onLog("تعذّر قراءة الرمز المحفوظ. احذفه وأدخله من جديد.")
                return
            }

            onLog("قفل الشاشة (keyevent 223)…")
            val lock = controller.lockScreen()
            if (!lock.ok) {
                onLog("فشل القفل: ${lock.error.ifBlank { "exit=${lock.exitCode}" }}")
                return
            }

            for (seconds in 5 downTo 1) {
                _ui.update { it.copy(countdown = seconds) }
                delay(1000)
            }
            _ui.update { it.copy(countdown = 0) }

            onLog(if (controller.isScreenOn) "تنبيه: الشاشة ما زالت تعمل." else "الشاشة مطفأة ✓")
            if (!controller.isLocked) {
                onLog("تنبيه: الجهاز غير مقفل بعد. فعّل \"القفل الفوري بزر التشغيل\".")
            }

            val result = controller.unlock(pin, _ui.value.method) { onLog(it) }
            onLog(
                when (result) {
                    UnlockResult.Success -> "✅ نجح الفتح."
                    UnlockResult.AlreadyUnlocked -> "الجهاز لم يكن مقفلاً، فلم يُدخَل الرمز."
                    UnlockResult.StillLocked ->
                        "❌ أُرسلت الأوامر لكن الجهاز ما زال مقفلاً. جرّب الطريقة الأخرى."
                    is UnlockResult.Failed -> "❌ فشلت خطوة \"${result.step}\": ${result.error}"
                },
            )
        } finally {
            pin?.fill('\u0000')
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun log(line: String) {
        _ui.update { it.copy(log = it.log + line) }
    }

    private fun remoteLog(line: String) {
        _ui.update { it.copy(remoteLog = it.remoteLog + line) }
    }
}
