package com.tabletunlock.net

import android.content.Context
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket

/**
 * الجوال في وضع الاستماع: يسجّل خدمة NSD، ويستقبل اتصالات التابلت.
 * في المرحلة 2 يدعم فقط "ping": يتحقق أن المُرسِل هو التابلت المقترن، ثم يردّ.
 * ستُضاف معالجة "unlock" (الإشعار + البصمة) في المرحلة 3.
 */
class PhoneListener(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.PHONE)
    private val nsd = NsdHelper(appContext)

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope, onLog: (String) -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(0)
                serverSocket = server
                val name = store.ownServiceName()
                nsd.register(name, server.localPort)
                onLog("يستمع باسم \"$name\" على المنفذ ${server.localPort} ✓")
                while (isActive) {
                    val socket = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (isActive) onLog("توقف الاستماع: ${e.message}") else Unit
                        break
                    }
                    launch(Dispatchers.IO) { handle(socket, onLog) }
                }
            } catch (e: Exception) {
                onLog("تعذّر بدء الاستماع: ${e.message}")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        nsd.unregister()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handle(socket: Socket, onLog: (String) -> Unit) {
        socket.use { s ->
            val reader = Wire.reader(s.getInputStream())
            val writer = Wire.writer(s.getOutputStream())
            val request = Wire.receive(reader) ?: return
            val tabletKey = store.peerPublicKey
            if (tabletKey == null) {
                Wire.send(writer, JSONObject().put("ok", false).put("error", "not_paired"))
                return
            }
            val type = request.optString("type")
            val nonce = request.optString("nonce")
            val ts = request.optLong("ts")
            val signed = "$type|$nonce|$ts".toByteArray()
            val validSig = SigningKeys.verify(
                SigningKeys.decodePublic(tabletKey),
                signed,
                SigningKeys.unb64(request.optString("sig")),
            )
            val fresh = kotlin.math.abs(System.currentTimeMillis() - ts) <= FRESH_WINDOW_MS

            if (!validSig) {
                onLog("رُفض طلب: توقيع غير صالح.")
                Wire.send(writer, JSONObject().put("ok", false).put("error", "bad_signature"))
                return
            }
            if (!fresh) {
                onLog("رُفض طلب: تجاوز المهلة الزمنية.")
                Wire.send(writer, JSONObject().put("ok", false).put("error", "stale"))
                return
            }

            when (type) {
                "ping" -> {
                    onLog("طلب اختبار اتصال موثّق من التابلت ✓")
                    Wire.send(writer, JSONObject().put("ok", true).put("type", "pong"))
                }
                else -> Wire.send(writer, JSONObject().put("ok", false).put("error", "unsupported"))
            }
        }
    }

    private companion object {
        const val FRESH_WINDOW_MS = 30_000L
    }
}
