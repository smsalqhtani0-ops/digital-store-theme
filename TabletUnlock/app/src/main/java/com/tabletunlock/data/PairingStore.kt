package com.tabletunlock.data

import android.content.Context

/**
 * حالة الاقتران. كل وضع يحفظ المفتاح العام للطرف الآخر.
 * التابلت يحفظ أيضاً اسم خدمة الجوال لاكتشافه على الشبكة.
 */
class PairingStore(context: Context, mode: AppMode) {
    private val prefs = context.getSharedPreferences("pairing_${mode.name}", Context.MODE_PRIVATE)

    val paired: Boolean get() = prefs.contains(KEY_PEER_PUBLIC)

    var peerPublicKey: String?
        get() = prefs.getString(KEY_PEER_PUBLIC, null)
        set(value) = prefs.edit().putString(KEY_PEER_PUBLIC, value).apply()

    var peerServiceName: String?
        get() = prefs.getString(KEY_PEER_SERVICE, null)
        set(value) = prefs.edit().putString(KEY_PEER_SERVICE, value).apply()

    /** اسم خدمة هذا الجوال على الشبكة (يُولَّد مرة ويُثبَّت). */
    fun ownServiceName(): String {
        prefs.getString(KEY_OWN_SERVICE, null)?.let { return it }
        val name = "TabletUnlock-${(100000..999999).random()}"
        prefs.edit().putString(KEY_OWN_SERVICE, name).apply()
        return name
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_PEER_PUBLIC = "peer_public"
        const val KEY_PEER_SERVICE = "peer_service"
        const val KEY_OWN_SERVICE = "own_service"
    }
}
