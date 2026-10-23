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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * منطق الجوال لمعالجة طلب وارد (عبر أي نقل).
 * يتحقق أن المُرسِل هو التابلت المقترن، ثم: "ping" ردّ مباشر، "unlock" بصمة.
 */
class PhoneRequestHandler(context: Context) : RequestHandler {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.PHONE)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val seenNonces = LinkedHashSet<String>()

    override suspend fun handle(link: Link, onLog: (String) -> Unit) {
        link.use {
            val request = Wire.receive(link.reader) ?: return
            val tabletKey = store.peerPublicKey
            if (tabletKey == null) {
                Wire.send(link.writer, reject("not_paired"))
                return
            }
            val type = request.optString("type")
            val nonce = request.optString("nonce")
            val ts = request.optLong("ts")
            val signed = "$type|$nonce|$ts".toByteArray()

            if (!SigningKeys.verify(SigningKeys.decodePublic(tabletKey), signed, SigningKeys.unb64(request.optString("sig")))) {
                onLog("رُفض طلب: توقيع غير صالح.")
                Wire.send(link.writer, reject("bad_signature"))
                return
            }
            if (kotlin.math.abs(System.currentTimeMillis() - ts) > FRESH_WINDOW_MS) {
                onLog("رُفض طلب: تجاوز المهلة الزمنية.")
                Wire.send(link.writer, reject("stale"))
                return
            }
            if (!seenNonces.add(nonce)) {
                onLog("رُفض طلب: nonce مستخدم سابقاً.")
                Wire.send(link.writer, reject("replay"))
                return
            }
            if (seenNonces.size > MAX_NONCES) {
                seenNonces.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }

            when (type) {
                "ping" -> {
                    onLog("طلب اختبار اتصال موثّق عبر ${link.transport} ✓")
                    Wire.send(link.writer, JSONObject().put("ok", true).put("type", "pong"))
                }
                "unlock" -> {
                    onLog("طلب فتح وارد عبر ${link.transport} — بانتظار البصمة…")
                    val pending = UnlockRequestBus.open(nonce, ts)
                    val shownDirectly = showPrompt(onLog)
                    val signature = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { pending.result.await() }
                    UnlockRequestBus.clear(pending)
                    if (!shownDirectly) UnlockNotifier.cancel(appContext)
                    if (signature != null) {
                        onLog("تمت الموافقة بالبصمة ✓")
                        Wire.send(link.writer, JSONObject().put("ok", true).put("sig", SigningKeys.b64(signature)))
                    } else {
                        onLog("رُفض الطلب أو انتهت المهلة بدون بصمة.")
                        Wire.send(link.writer, reject("denied_or_timeout"))
                    }
                }
                else -> Wire.send(link.writer, reject("unsupported"))
            }
        }
    }

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
