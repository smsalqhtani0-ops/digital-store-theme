package com.tabletunlock.net

import kotlinx.coroutines.CompletableDeferred

/**
 * جسر بين خدمة الاستماع (التي تنتظر الرد) وشاشة البصمة (التي تنتجه).
 * يحمل طلب فتح واحداً في كل مرة.
 */
object UnlockRequestBus {
    data class Pending(
        val nonce: String,
        val ts: Long,
        /** يُكمَّل بتوقيع الجوال عند نجاح البصمة، أو بـ null عند الرفض/الإلغاء. */
        val result: CompletableDeferred<ByteArray?> = CompletableDeferred(),
    ) {
        /** النص الذي يوقّعه الجوال، ويجب أن يطابق ما يتحقق منه التابلت. */
        fun payload(): ByteArray = "unlock|$nonce|$ts".toByteArray()
    }

    @Volatile
    var pending: Pending? = null
        private set

    fun open(nonce: String, ts: Long): Pending {
        val p = Pending(nonce, ts)
        pending = p
        return p
    }

    fun clear(p: Pending) {
        if (pending === p) pending = null
    }
}
