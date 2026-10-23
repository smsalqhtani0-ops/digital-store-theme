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

/** الجوال يبدأ الفتح: بعد البصمة، يتصل بالتابلت ويرسل أمر فتح موقّعاً. */
class PhoneInitiator(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.PHONE)
    private val connector = Connector(appContext)

    fun newNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SigningKeys.b64(bytes)
    }

    fun payload(nonce: String, ts: Long): ByteArray = "unlock_now|$nonce|$ts".toByteArray()

    /** يرسل الأمر الموقّع ويعيد نصّ النتيجة لعرضه للمستخدم. */
    suspend fun send(nonce: String, ts: Long, signature: ByteArray, onLog: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            if (!store.paired) return@withContext "غير مقترن مع التابلت."
            val link = connector.open(store.peerBtName, store.peerServiceName) { onLog(it) }
                ?: return@withContext "تعذّر الوصول للتابلت. تأكد أنه يستقبل وأن البلوتوث/الواي فاي يعمل."
            link.use {
                try {
                    Wire.send(
                        link.writer,
                        JSONObject().apply {
                            put("type", "unlock_now")
                            put("nonce", nonce)
                            put("ts", ts)
                            put("sig", SigningKeys.b64(signature))
                        },
                    )
                    val reply = receiveWithin(link, 25_000) ?: return@withContext "انتهت المهلة بلا رد من التابلت."
                    when {
                        reply.optBoolean("ok") -> reply.optString("status").ifBlank { "✅ تم." }
                        else -> mapError(reply.optString("error"))
                    }
                } catch (e: Exception) {
                    "خطأ: ${e.message}"
                }
            }
        }

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
        "shizuku_off" -> "التابلت مُستقبِل لكن Shizuku متوقف عليه."
        "no_pin", "pin_error" -> "لا يوجد رمز محفوظ في التابلت."
        "not_paired" -> "التابلت غير مقترن."
        "bad_signature" -> "رفض التابلت التوقيع."
        "replay", "stale" -> "طلب غير صالح (قديم/مكرر)."
        else -> "رُفض: $code"
    }
}
