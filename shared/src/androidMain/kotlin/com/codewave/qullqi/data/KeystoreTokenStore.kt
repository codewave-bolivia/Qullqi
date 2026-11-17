package com.codewave.qullqi.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.codewave.qullqi.data.remote.TokenStore
import com.codewave.qullqi.domain.model.UserId
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Renewal credentials encrypted with an AES-256-GCM key held by the Android Keystore; the
 * preferences file only ever contains ciphertext (spec 004, FR-005, research R2).
 *
 * ponytail: the key is not bound to biometric authentication, so the fingerprint is an app-level
 * gate (FR-008); on a rooted device the credential could be decrypted. Upgrade path: a second key
 * with setUserAuthenticationRequired(true) + BiometricPrompt.CryptoObject, decrypting only on cold
 * start and keeping the value in memory for the session (research R3).
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences("qullqi_secure", Context.MODE_PRIVATE)

    override fun refreshToken(userId: UserId): String? {
        val stored = prefs.getString(key(userId), null) ?: return null
        return try {
            val (iv, data) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key lost (backup restored on another device, key invalidated): the PIN is required again.
            clear(userId)
            null
        }
    }

    override fun saveRefreshToken(userId: UserId, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val data = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs.edit().putString(key(userId), value).apply()
    }

    override fun clear(userId: UserId) {
        prefs.edit().remove(key(userId)).apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(userId: UserId) = "rt_${userId.value}"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "qullqi_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
