package com.tabletunlock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs
import com.tabletunlock.data.PairingStore
import com.tabletunlock.pairing.PairingPayload
import com.tabletunlock.pairing.PhonePairing
import com.tabletunlock.service.ListeningState
import com.tabletunlock.service.PhoneUnlockService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhoneViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val prefs = AppPrefs(app)
    private val pairingStore = PairingStore(app, AppMode.PHONE)
    private val pairing = PhonePairing(app)

    private val _paired = MutableStateFlow(pairingStore.paired)
    val paired: StateFlow<Boolean> = _paired.asStateFlow()

    val listening: StateFlow<Boolean> = ListeningState.running
    val log: StateFlow<List<String>> = ListeningState.log

    fun onScanned(text: String) {
        val payload = PairingPayload.decode(text)
        if (payload == null) {
            ListeningState.appendLog("رمز QR غير صالح. امسح الرمز المعروض في التابلت.")
            return
        }
        ListeningState.resetLog()
        ListeningState.appendLog("جارٍ الاقتران مع التابلت…")
        viewModelScope.launch {
            pairing.pair(payload)
                .onSuccess {
                    _paired.value = true
                    ListeningState.appendLog("✅ تم الاقتران بنجاح.")
                }
                .onFailure { ListeningState.appendLog("❌ فشل الاقتران: ${it.message}") }
        }
    }

    fun startListening() {
        prefs.listeningEnabled = true
        PhoneUnlockService.start(appContext)
    }

    fun stopListening() {
        prefs.listeningEnabled = false
        PhoneUnlockService.stop(appContext)
    }

    fun unpair() {
        stopListening()
        pairingStore.clear()
        _paired.value = false
    }
}
