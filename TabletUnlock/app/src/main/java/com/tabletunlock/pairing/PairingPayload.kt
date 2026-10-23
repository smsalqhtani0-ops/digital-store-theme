package com.tabletunlock.pairing

import org.json.JSONObject

/** محتوى رمز QR الذي يعرضه التابلت ويمسحه الجوال. */
data class PairingPayload(
    val version: Int,
    val host: String,
    val port: Int,
    val secret: String,
    val tabletPublicKey: String,
    val tabletBtName: String?,
    val tabletServiceName: String,
) {
    fun encode(): String = JSONObject().apply {
        put("v", version)
        put("host", host)
        put("port", port)
        put("secret", secret)
        put("tpk", tabletPublicKey)
        put("tbt", tabletBtName ?: "")
        put("tsn", tabletServiceName)
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
                tabletBtName = json.optString("tbt").ifBlank { null },
                tabletServiceName = json.getString("tsn"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
