package com.tabletunlock.pairing

import android.content.Context
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import com.tabletunlock.net.Bt
import com.tabletunlock.net.NetUtil
import com.tabletunlock.net.Wire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ServerSocket
import java.security.SecureRandom

/**
 * التابلت أثناء الاقتران: يفتح socket مؤقتاً، يعرض بياناته في QR،
 * وينتظر اتصال الجوال ليتبادلا المفاتيح العامة.
 */
class TabletPairing(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.TABLET)

    sealed interface Result {
        data class Ready(val payload: PairingPayload) : Result
        data class Error(val message: String) : Result
    }

    /** يجهّز بيانات QR ويبدأ الانتظار. يعيد Ready مع ما يُعرض، أو Error. */
    suspend fun begin(onPaired: (phoneName: String) -> Unit, onFailure: (String) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val host = NetUtil.localIpv4()
                ?: return@withContext Result.Error("لا يوجد اتصال واي فاي. تأكد أن الجهازين على نفس الشبكة.")
            val secret = randomSecret()
            val tabletPublic = SigningKeys.encodePublic(SigningKeys.tabletPublicKey())
            val server = try {
                ServerSocket(0)
            } catch (e: Exception) {
                return@withContext Result.Error("تعذّر فتح منفذ الاقتران: ${e.message}")
            }
            server.soTimeout = PAIRING_TIMEOUT_MS

            val btName = Bt.ownName(appContext)
            val payload = PairingPayload(PairingPayload.VERSION, host, server.localPort, secret, tabletPublic, btName)

            Thread {
                try {
                    server.use {
                        val socket = it.accept()
                        socket.use { s ->
                            val reader = Wire.reader(s.getInputStream())
                            val writer = Wire.writer(s.getOutputStream())
                            val msg = Wire.receive(reader)
                            if (msg == null || msg.getString("secret") != secret) {
                                Wire.send(writer, JSONObject().put("ok", false))
                                onFailure("فشل التحقق من رمز الاقتران.")
                                return@Thread
                            }
                            store.peerPublicKey = msg.getString("ppk")
                            store.peerServiceName = msg.getString("service")
                            store.peerBtName = msg.optString("bt").ifBlank { null }
                            Wire.send(writer, JSONObject().put("ok", true))
                            onPaired(msg.getString("service"))
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    onFailure("انتهت مهلة الاقتران. أعد المحاولة.")
                } catch (e: Exception) {
                    onFailure("خطأ في الاقتران: ${e.message}")
                }
            }.apply { isDaemon = true }.start()

            Result.Ready(payload)
        }

    private fun randomSecret(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return SigningKeys.b64(bytes)
    }

    private companion object {
        const val PAIRING_TIMEOUT_MS = 120_000
    }
}
