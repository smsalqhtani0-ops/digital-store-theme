package com.tabletunlock.pairing

import org.json.JSONObject

/** محتوى رمز QR الذي يعرضه التابلت ويمسحه الجوال. */
data class PairingPayload(
    val version: Int,
    val host: String,
    val port: Int,
    val secret: String,
    val tabletPublicKey: String,
) {
    fun encode(): String = JSONObject().apply {
        put("v", version)
        put("host", host)
        put("port", port)
        put("secret", secret)
        put("tpk", tabletPublicKey)
    }.toString()

    companion object {
        const val VERSION = 1

        fun decode(text: String): PairingPayload? = try {
            val json = JSONObject(text)
            PairingPayload(
                version = json.getInt("v"),
                host = json.getString("host"),
                port = json.getInt("port"),
                secret = json.getString("secret"),
                tabletPublicKey = json.getString("tpk"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
