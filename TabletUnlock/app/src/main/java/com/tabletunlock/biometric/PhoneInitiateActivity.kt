package com.tabletunlock.biometric

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.net.PhoneInitiator
import com.tabletunlock.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** يُفتح من بلاطة الإعدادات السريعة: بصمة ← إرسال أمر الفتح للتابلت. */
class PhoneInitiateActivity : FragmentActivity() {
    private val initiator by lazy { PhoneInitiator(this) }
    private var statusText by mutableStateOf("ضع بصمتك لفتح التابلت…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            AppTheme {
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("فتح التابلت", style = MaterialTheme.typography.headlineSmall)
                            Text(statusText)
                        }
                    }
                }
            }
        }
        startBiometric()
    }

    private fun startBiometric() {
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            finishWith("لا توجد بصمة مُسجَّلة على الجوال.")
            return
        }
        val signature = try {
            SigningKeys.phoneSignatureObject()
        } catch (e: Exception) {
            finishWith("لم يتم الاقتران بعد، أو تعذّر المفتاح.")
            return
        }

        val nonce = initiator.newNonce()
        val ts = System.currentTimeMillis()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val sig = result.cryptoObject?.signature ?: return finishWith("خطأ في التوقيع.")
                    sig.update(initiator.payload(nonce, ts))
                    val signed = sig.sign()
                    statusText = "جارٍ إرسال الطلب للتابلت…"
                    lifecycleScope.launch {
                        val resultText = initiator.send(nonce, ts, signed) { statusText = it }
                        finishWith(resultText)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finishWith("أُلغيت البصمة.")
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح التابلت")
            .setSubtitle("ضع بصمتك لإرسال أمر الفتح")
            .setNegativeButtonText("إلغاء")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
    }

    private fun finishWith(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}
