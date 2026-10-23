package com.tabletunlock.net

import android.content.Context
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom

/** التابلت كعميل: يفتح قناة (بلوتوث ثم واي فاي) ويرسل طلباً موقّعاً. */
class TabletClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.TABLET)
    private val connector = Connector(appContext)

    sealed interface Outcome {
        data object Success : Outcome
        data class Failed(val message: String) : Outcome
    }

    sealed interface UnlockOutcome {
        data object Approved : UnlockOutcome
        data class Rejected(val message: String) : UnlockOutcome
        data class Failed(val message: String) : UnlockOutcome
    }

    suspend fun testConnection(onLog: (String) -> Unit): Outcome = withContext(Dispatchers.IO) {
        if (!store.paired) return@withContext Outcome.Failed("لم يتم الاقتران بعد.")
        val link = connector.open(store.peerBtName, store.peerServiceName) { onLog(it) }
            ?: return@withContext Outcome.Failed("تعذّر الوصول للجوال عبر البلوتوث أو الواي فاي.")
        link.use {
            try {
                val nonce = randomNonce()
                val ts = System.currentTimeMillis()
                sendSigned(link, "ping", nonce, ts)
                val reply = receiveWithin(link, 10_000)
                if (reply?.optBoolean("ok") == true) Outcome.Success
                else Outcome.Failed("ردّ الجوال بالرفض: ${reply?.optString("error")}")
            } catch (e: Exception) {
                Outcome.Failed("خطأ في الاتصال: ${e.message}")
            }
        }
    }

    suspend fun requestUnlock(onLog: (String) -> Unit): UnlockOutcome = withContext(Dispatchers.IO) {
        if (!store.paired) return@withContext UnlockOutcome.Failed("لم يتم الاقتران بعد.")
        val phoneKey = store.peerPublicKey
            ?: return@withContext UnlockOutcome.Failed("لا يوجد مفتاح للجوال. أعد الاقتران.")
        val link = connector.open(store.peerBtName, store.peerServiceName) { onLog(it) }
            ?: return@withContext UnlockOutcome.Failed("تعذّر الوصول للجوال. تأكد أنه في وضع الاستماع.")
        onLog("إرسال طلب الفتح… افتح الإشعار/البصمة في الجوال.")
        link.use {
            try {
                val nonce = randomNonce()
                val ts = System.currentTimeMillis()
                val signed = "unlock|$nonce|$ts".toByteArray()
                sendSigned(link, "unlock", nonce, ts)
                val reply = receiveWithin(link, REPLY_TIMEOUT_MS)
                    ?: return@withContext UnlockOutcome.Rejected("انتهت المهلة بانتظار البصمة في الجوال.")
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
            } catch (e: Exception) {
                UnlockOutcome.Failed("خطأ في الاتصال: ${e.message}")
            }
        }
    }

    private fun sendSigned(link: Link, type: String, nonce: String, ts: Long) {
        val signed = "$type|$nonce|$ts".toByteArray()
        Wire.send(
            link.writer,
            JSONObject().apply {
                put("type", type)
                put("nonce", nonce)
                put("ts", ts)
                put("sig", SigningKeys.b64(SigningKeys.signWithTablet(signed)))
            },
        )
    }

    /** قراءة مع مهلة تعمل للبلوتوث والواي فاي: مراقب يغلق القناة عند التأخّر. */
    private suspend fun receiveWithin(link: Link, timeoutMs: Long): JSONObject? = coroutineScope {
        val watchdog = launch {
            delay(timeoutMs)
            runCatching { link.close() }
        }
        try {
            Wire.receive(link.reader)
        } catch (e: Exception) {
            null
        } finally {
            watchdog.cancel()
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
        const val REPLY_TIMEOUT_MS = 40_000L
        const val FRESH_WINDOW_MS = 30_000L
    }
}
