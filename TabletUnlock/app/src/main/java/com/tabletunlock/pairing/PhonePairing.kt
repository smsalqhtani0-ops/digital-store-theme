package com.tabletunlock.pairing

import android.content.Context
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import com.tabletunlock.net.Bt
import com.tabletunlock.net.Wire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/** الجوال بعد مسح QR: يتصل بالتابلت ويتبادل المفاتيح العامة. */
class PhonePairing(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.PHONE)

    suspend fun pair(payload: PairingPayload): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val phonePublic = SigningKeys.encodePublic(SigningKeys.phonePublicKey())
            val serviceName = store.ownServiceName()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(payload.host, payload.port), CONNECT_TIMEOUT_MS)
                val writer = Wire.writer(socket.getOutputStream())
                val reader = Wire.reader(socket.getInputStream())
                Wire.send(
                    writer,
                    JSONObject().apply {
                        put("secret", payload.secret)
                        put("ppk", phonePublic)
                        put("service", serviceName)
                        put("bt", Bt.ownName(appContext) ?: "")
                    },
                )
                val ack = Wire.receive(reader)
                if (ack?.optBoolean("ok") != true) {
                    return@withContext kotlin.Result.failure(Exception("رفض التابلت الاقتران."))
                }
                store.peerPublicKey = payload.tabletPublicKey
                store.peerBtName = payload.tabletBtName
                store.peerServiceName = payload.tabletServiceName
                kotlin.Result.success(Unit)
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8000
    }
}
