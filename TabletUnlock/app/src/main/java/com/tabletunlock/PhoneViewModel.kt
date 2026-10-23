package com.tabletunlock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import com.tabletunlock.net.PhoneListener
import com.tabletunlock.pairing.PairingPayload
import com.tabletunlock.pairing.PhonePairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneUiState(
    val paired: Boolean = false,
    val listening: Boolean = false,
    val log: List<String> = emptyList(),
)

class PhoneViewModel(app: Application) : AndroidViewModel(app) {
    private val pairingStore = PairingStore(app, AppMode.PHONE)
    private val pairing = PhonePairing(app)
    private val listener = PhoneListener(app)

    private val _ui = MutableStateFlow(PhoneUiState(paired = pairingStore.paired))
    val ui: StateFlow<PhoneUiState> = _ui.asStateFlow()

    fun onScanned(text: String) {
        val payload = PairingPayload.decode(text)
        if (payload == null) {
            log("رمز QR غير صالح. امسح الرمز المعروض في التابلت.")
            return
        }
        log("جارٍ الاقتران مع التابلت…")
        viewModelScope.launch {
            pairing.pair(payload)
                .onSuccess {
                    _ui.update { it.copy(paired = true) }
                    log("✅ تم الاقتران بنجاح.")
                }
                .onFailure { log("❌ فشل الاقتران: ${it.message}") }
        }
    }

    fun toggleListening() {
        if (_ui.value.listening) {
            listener.stop()
            _ui.update { it.copy(listening = false, log = it.log + "توقف الاستماع.") }
        } else {
            _ui.update { it.copy(listening = true, log = emptyList()) }
            listener.start(viewModelScope) { line -> log(line) }
        }
    }

    fun unpair() {
        listener.stop()
        pairingStore.clear()
        _ui.update { PhoneUiState(paired = false) }
    }

    private fun log(line: String) = _ui.update { it.copy(log = it.log + line) }

    override fun onCleared() {
        listener.stop()
    }
}
