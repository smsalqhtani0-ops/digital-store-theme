package com.tabletunlock.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * مفاتيح ECDSA P-256 في Android Keystore.
 * - مفتاح التابلت: يوقّع طلبات الفتح، بلا قيد بصمة (التابلت لا يملك بصمة المستخدم).
 * - مفتاح الجوال: مقيّد بالبصمة (setUserAuthenticationRequired)، ويُستخدم عبر CryptoObject فقط.
 */
object SigningKeys {
    const val TABLET_ALIAS = "tablet_sign_key"
    const val PHONE_ALIAS = "phone_sign_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALGORITHM = "SHA256withECDSA"

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    fun tabletPublicKey(): PublicKey {
        ensureKey(TABLET_ALIAS, biometric = false)
        return keyStore().getCertificate(TABLET_ALIAS).publicKey
    }

    fun phonePublicKey(): PublicKey {
        ensureKey(PHONE_ALIAS, biometric = true)
        return keyStore().getCertificate(PHONE_ALIAS).publicKey
    }

    fun signWithTablet(data: ByteArray): ByteArray {
        ensureKey(TABLET_ALIAS, biometric = false)
        val key = keyStore().getKey(TABLET_ALIAS, null) as PrivateKey
        return Signature.getInstance(ALGORITHM).run {
            initSign(key)
            update(data)
            sign()
        }
    }

    /** توقيع مُهيّأ بمفتاح الجوال، يُلفّ في BiometricPrompt.CryptoObject (يُستخدم في المرحلة 3). */
    fun phoneSignatureObject(): Signature {
        ensureKey(PHONE_ALIAS, biometric = true)
        val key = keyStore().getKey(PHONE_ALIAS, null) as PrivateKey
        return Signature.getInstance(ALGORITHM).apply { initSign(key) }
    }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey)
            update(data)
            verify(signature)
        }
    } catch (e: Exception) {
        false
    }

    private fun ensureKey(alias: String, biometric: Boolean) {
        val ks = keyStore()
        if (ks.containsAlias(alias)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
        if (biometric) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    fun encodePublic(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

    fun decodePublic(encoded: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(encoded)))

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
