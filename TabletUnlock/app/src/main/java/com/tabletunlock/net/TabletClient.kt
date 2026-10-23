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

    sealed interface UnlockOutcome {
        data object Approved : UnlockOutcome
        data class Rejected(val message: String) : UnlockOutcome
        data class Failed(val message: String) : UnlockOutcome
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

    /** المرحلة 3: يطلب الفتح، وينتظر توقيع الجوال بعد البصمة، ويتحقق منه. */
    suspend fun requestUnlock(onLog: (String) -> Unit): UnlockOutcome = withContext(Dispatchers.IO) {
        val serviceName = store.peerServiceName
            ?: return@withContext UnlockOutcome.Failed("لم يتم الاقتران بعد.")
        val phoneKey = store.peerPublicKey
            ?: return@withContext UnlockOutcome.Failed("لا يوجد مفتاح للجوال. أعد الاقتران.")
        onLog("البحث عن الجوال على الشبكة…")
        val target = nsd.resolveOnce(serviceName)
            ?: return@withContext UnlockOutcome.Failed("لم يُعثر على الجوال. تأكد أنه في وضع الاستماع.")
        onLog("إرسال طلب الفتح… افتح الإشعار في الجوال وضع بصمتك.")

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.first, target.second), CONNECT_TIMEOUT_MS)
                socket.soTimeout = REPLY_TIMEOUT_MS
                val writer = Wire.writer(socket.getOutputStream())
                val reader = Wire.reader(socket.getInputStream())
                val nonce = randomNonce()
                val ts = System.currentTimeMillis()
                val signed = "unlock|$nonce|$ts".toByteArray()
                Wire.send(
                    writer,
                    JSONObject().apply {
                        put("type", "unlock")
                        put("nonce", nonce)
                        put("ts", ts)
                        put("sig", SigningKeys.b64(SigningKeys.signWithTablet(signed)))
                    },
                )
                val reply = Wire.receive(reader)
                    ?: return@withContext UnlockOutcome.Failed("انقطع الاتصال قبل وصول الرد.")
                if (!reply.optBoolean("ok")) {
                    return@withContext UnlockOutcome.Rejected(mapError(reply.optString("error")))
                }
                val okSig = SigningKeys.verify(
                    SigningKeys.decodePublic(phoneKey),
                    signed,
                    SigningKeys.unb64(reply.optString("sig")),
                )
                val fresh = kotlin.math.abs(System.currentTimeMillis() - ts) <= FRESH_WINDOW_MS
                when {
                    !okSig -> UnlockOutcome.Rejected("توقيع الجوال غير صالح — رُفض.")
                    !fresh -> UnlockOutcome.Rejected("تجاوز الرد 30 ثانية — رُفض.")
                    else -> UnlockOutcome.Approved
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            UnlockOutcome.Rejected("انتهت المهلة بانتظار البصمة في الجوال.")
        } catch (e: Exception) {
            UnlockOutcome.Failed("خطأ في الاتصال: ${e.message}")
        }
    }

    private fun mapError(code: String): String = when (code) {
        "denied_or_timeout" -> "رُفض الطلب في الجوال أو انتهت المهلة."
        "replay" -> "nonce مكرر — رُفض."
        "stale" -> "الطلب قديم — رُفض."
        "bad_signature" -> "توقيع التابلت غير مقبول في الجوال."
        "not_paired" -> "الجوال غير مقترن."
        else -> "رُفض الطلب: $code"
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SigningKeys.b64(bytes)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8000
        const val REPLY_TIMEOUT_MS = 40_000
        const val FRESH_WINDOW_MS = 30_000L
    }
}
