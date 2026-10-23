package com.tabletunlock.net

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.tabletunlock.biometric.UnlockApproveActivity
import com.tabletunlock.biometric.UnlockNotifier
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.PairingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket

/**
 * الجوال في وضع الاستماع: يسجّل خدمة NSD ويستقبل اتصالات التابلت.
 * - "ping": اختبار اتصال موثّق (المرحلة 2).
 * - "unlock": يتحقق أن المُرسِل هو التابلت المقترن، يعرض إشعاراً عالي الأولوية،
 *   وينتظر توقيع البصمة ثم يردّ به (المرحلة 3).
 */
class PhoneListener(context: Context) {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.PHONE)
    private val nsd = NsdHelper(appContext)
    private val power = appContext.getSystemService(PowerManager::class.java)

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val seenNonces = LinkedHashSet<String>()

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
                        if (isActive) onLog("توقف الاستماع: ${e.message}")
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

    private suspend fun handle(socket: Socket, onLog: (String) -> Unit) {
        socket.use { s ->
            val reader = Wire.reader(s.getInputStream())
            val writer = Wire.writer(s.getOutputStream())
            val request = Wire.receive(reader) ?: return
            val tabletKey = store.peerPublicKey
            if (tabletKey == null) {
                Wire.send(writer, reject("not_paired"))
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
            if (!validSig) {
                onLog("رُفض طلب: توقيع غير صالح.")
                Wire.send(writer, reject("bad_signature"))
                return
            }
            if (kotlin.math.abs(System.currentTimeMillis() - ts) > FRESH_WINDOW_MS) {
                onLog("رُفض طلب: تجاوز المهلة الزمنية.")
                Wire.send(writer, reject("stale"))
                return
            }
            if (!seenNonces.add(nonce)) {
                onLog("رُفض طلب: nonce مستخدم سابقاً.")
                Wire.send(writer, reject("replay"))
                return
            }
            if (seenNonces.size > MAX_NONCES) {
                seenNonces.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }

            when (type) {
                "ping" -> {
                    onLog("طلب اختبار اتصال موثّق من التابلت ✓")
                    Wire.send(writer, JSONObject().put("ok", true).put("type", "pong"))
                }
                "unlock" -> {
                    onLog("طلب فتح وارد — بانتظار البصمة…")
                    val pending = UnlockRequestBus.open(nonce, ts)
                    val shownDirectly = showPrompt(onLog)
                    val signature = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { pending.result.await() }
                    UnlockRequestBus.clear(pending)
                    if (!shownDirectly) UnlockNotifier.cancel(appContext)
                    if (signature != null) {
                        onLog("تمت الموافقة بالبصمة ✓")
                        Wire.send(
                            writer,
                            JSONObject().put("ok", true).put("sig", SigningKeys.b64(signature)),
                        )
                    } else {
                        onLog("رُفض الطلب أو انتهت المهلة بدون بصمة.")
                        Wire.send(writer, reject("denied_or_timeout"))
                    }
                }
                else -> Wire.send(writer, reject("unsupported"))
            }
        }
    }

    /**
     * إن كانت الشاشة مضاءة (والإذن بالظهور فوق التطبيقات ممنوح)، تُفتح شاشة البصمة مباشرة.
     * وإلا يُعرض إشعار عالي الأولوية يظهر على شاشة القفل. يعيد true إن فُتحت الشاشة مباشرة.
     */
    private fun showPrompt(onLog: (String) -> Unit): Boolean {
        val interactive = power.isInteractive
        val canOverlay = Settings.canDrawOverlays(appContext)
        return if (interactive && canOverlay) {
            onLog("الشاشة مضاءة — إظهار البصمة مباشرة.")
            appContext.startActivity(
                Intent(appContext, UnlockApproveActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            true
        } else {
            UnlockNotifier.show(appContext)
            false
        }
    }

    private fun reject(error: String) = JSONObject().put("ok", false).put("error", error)

    private companion object {
        const val FRESH_WINDOW_MS = 30_000L
        const val APPROVAL_TIMEOUT_MS = 30_000L
        const val MAX_NONCES = 200
    }
}
