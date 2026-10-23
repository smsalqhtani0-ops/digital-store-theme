package com.tabletunlock.net

import android.content.Context
import com.tabletunlock.crypto.SigningKeys
import com.tabletunlock.data.AppMode
import com.tabletunlock.data.AppPrefs
import com.tabletunlock.data.PairingStore
import com.tabletunlock.security.PinStore
import com.tabletunlock.shizuku.ShizukuManager
import com.tabletunlock.shizuku.ShizukuState
import com.tabletunlock.unlock.UnlockController
import com.tabletunlock.unlock.UnlockResult
import org.json.JSONObject

/**
 * منطق التابلت لمعالجة طلب فتح يبدأ من الجوال (بلاطة الإعدادات السريعة).
 * الجوال يكون قد وضع بصمته ووقّع، فيتحقق التابلت من التوقيع ثم يفتح عبر Shizuku.
 */
class TabletRequestHandler(context: Context) : RequestHandler {
    private val appContext = context.applicationContext
    private val store = PairingStore(appContext, AppMode.TABLET)
    private val prefs = AppPrefs(appContext)
    private val pinStore = PinStore(appContext)
    private val controller = UnlockController(appContext)
    private val seenNonces = LinkedHashSet<String>()

    override suspend fun handle(link: Link, onLog: (String) -> Unit) {
        link.use {
            val request = Wire.receive(link.reader) ?: return
            val phoneKey = store.peerPublicKey
            if (phoneKey == null) {
                Wire.send(link.writer, reject("not_paired"))
                return
            }
            val type = request.optString("type")
            val nonce = request.optString("nonce")
            val ts = request.optLong("ts")
            val signed = "$type|$nonce|$ts".toByteArray()

            if (!SigningKeys.verify(SigningKeys.decodePublic(phoneKey), signed, SigningKeys.unb64(request.optString("sig")))) {
                onLog("رُفض طلب من الجوال: توقيع غير صالح.")
                Wire.send(link.writer, reject("bad_signature"))
                return
            }
            if (kotlin.math.abs(System.currentTimeMillis() - ts) > FRESH_WINDOW_MS) {
                Wire.send(link.writer, reject("stale"))
                return
            }
            if (!seenNonces.add(nonce)) {
                Wire.send(link.writer, reject("replay"))
                return
            }
            if (seenNonces.size > MAX_NONCES) {
                seenNonces.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }

            if (type != "unlock_now") {
                Wire.send(link.writer, reject("unsupported"))
                return
            }

            if (ShizukuManager.state.value != ShizukuState.READY) {
                onLog("طلب فتح من الجوال، لكن Shizuku متوقف.")
                Wire.send(link.writer, reject("shizuku_off"))
                return
            }
            if (!pinStore.hasPin()) {
                Wire.send(link.writer, reject("no_pin"))
                return
            }

            onLog("طلب فتح موثّق من الجوال عبر ${link.transport} — جارٍ الفتح…")
            var pin: CharArray? = null
            try {
                pin = pinStore.load()
                if (pin == null) {
                    Wire.send(link.writer, reject("pin_error"))
                    return
                }
                val result = controller.unlock(pin, prefs.unlockMethod) { }
                val ok = result is UnlockResult.Success || result is UnlockResult.AlreadyUnlocked
                val status = when (result) {
                    UnlockResult.Success -> "فُتح التابلت ✓"
                    UnlockResult.AlreadyUnlocked -> "كان مفتوحاً."
                    UnlockResult.StillLocked -> "لم يُفتح (جرّب الطريقة الأخرى)."
                    is UnlockResult.Failed -> "فشل: ${result.step}"
                }
                onLog(status)
                Wire.send(link.writer, JSONObject().put("ok", ok).put("status", status))
            } finally {
                pin?.fill('\u0000')
            }
        }
    }

    private fun reject(error: String) = JSONObject().put("ok", false).put("error", error)

    private companion object {
        const val FRESH_WINDOW_MS = 30_000L
        const val MAX_NONCES = 200
    }
}
