package com.tabletunlock.net

import android.content.Context
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

/** التابلت كعميل: يكتشف الجوال عبر NSD ويرسل طلباً موقّعاً. */
class TabletClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.TABLET)
    private val nsd = NsdHelper(appContext)

    sealed interface Outcome {
        data object Success : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** المرحلة 2: اختبار اتصال موثّق (بدون فتح). */
    suspend fun testConnection(onLog: (String) -> Unit): Outcome = withContext(Dispatchers.IO) {
        val serviceName = store.peerServiceName
            ?: return@withContext Outcome.Failed("لم يتم الاقتران بعد.")
        onLog("البحث عن الجوال على الشبكة…")
        val target = nsd.resolveOnce(serviceName)
            ?: return@withContext Outcome.Failed("لم يُعثر على الجوال. تأكد أنه في وضع الاستماع وعلى نفس الواي فاي.")
        onLog("عُثر على الجوال: ${target.first}:${target.second}")

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.first, target.second), CONNECT_TIMEOUT_MS)
                val writer = Wire.writer(socket.getOutputStream())
                val reader = Wire.reader(socket.getInputStream())
                val nonce = randomNonce()
                val ts = System.currentTimeMillis()
                val signed = "ping|$nonce|$ts".toByteArray()
                Wire.send(
                    writer,
                    JSONObject().apply {
                        put("type", "ping")
                        put("nonce", nonce)
                        put("ts", ts)
                        put("sig", SigningKeys.b64(SigningKeys.signWithTablet(signed)))
                    },
                )
                val reply = Wire.receive(reader)
                if (reply?.optBoolean("ok") == true) {
                    Outcome.Success
                } else {
                    Outcome.Failed("ردّ الجوال بالرفض: ${reply?.optString("error")}")
                }
            }
        } catch (e: Exception) {
            Outcome.Failed("خطأ في الاتصال: ${e.message}")
        }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SigningKeys.b64(bytes)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8000
    }
}
