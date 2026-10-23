package com.tabletunlock.biometric

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.net.UnlockRequestBus
import com.tabletunlock.ui.theme.AppTheme

/**
 * تُفتح من إشعار طلب الفتح. تعرض BiometricPrompt مع CryptoObject،
 * وعند نجاح البصمة توقّع الطلب بمفتاح الجوال المقيّد بالبصمة، وتسلّم التوقيع للجسر.
 */
class UnlockApproveActivity : FragmentActivity() {

    private var statusText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            AppTheme {
                Scaffold { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("فتح التابلت", style = MaterialTheme.typography.headlineSmall)
                            Text(statusText.ifBlank { "ضع بصمتك للموافقة…" })
                        }
                    }
                }
            }
        }

        val pending = UnlockRequestBus.pending
        if (pending == null) {
            finish()
            return
        }
        promptBiometric(pending)
    }

    private fun promptBiometric(pending: UnlockRequestBus.Pending) {
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            statusText = "لا توجد بصمة مُسجَّلة على هذا الجوال."
            pending.result.complete(null)
            return
        }

        val signature = try {
            SigningKeys.phoneSignatureObject()
        } catch (e: Exception) {
            statusText = "تعذّر تجهيز مفتاح التوقيع: ${e.message}"
            pending.result.complete(null)
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val sig = result.cryptoObject!!.signature!!
                        sig.update(pending.payload())
                        pending.result.complete(sig.sign())
                    } catch (e: Exception) {
                        pending.result.complete(null)
                    }
                    UnlockNotifier.cancel(this@UnlockApproveActivity)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    pending.result.complete(null)
                    UnlockNotifier.cancel(this@UnlockApproveActivity)
                    finish()
                }

                override fun onAuthenticationFailed() {
                    statusText = "بصمة غير مطابقة، حاول مرة أخرى."
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح التابلت")
            .setSubtitle("ضع بصمتك للموافقة على فتح التابلت")
            .setNegativeButtonText("إلغاء")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        UnlockRequestBus.pending?.let {
            if (!it.result.isCompleted) it.result.complete(null)
        }
    }
}
