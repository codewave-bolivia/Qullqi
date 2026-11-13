package com.codewave.qullqi.data

import com.codewave.qullqi.domain.security.PinHash
import com.codewave.qullqi.domain.security.PinHasher
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ponytail: a 4-digit PIN is brute-forceable offline (10k combinations) whatever the hash; the real
// protection is the 3-attempt lock plus app-private storage. Upgrade path when real data exists:
// wrap the derived key with an Android Keystore (hardware-backed) key.
class Pbkdf2PinHasher(
    private val iterations: Int = ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) : PinHasher {

    override fun hash(pin: String): PinHash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinHash(b64(derive(pin, salt, iterations)), b64(salt), iterations)
    }

    override fun verify(pin: String, stored: PinHash): Boolean {
        val salt = Base64.getDecoder().decode(stored.saltB64)
        val expected = Base64.getDecoder().decode(stored.hashB64)
        return MessageDigest.isEqual(derive(pin, salt, stored.iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    companion object {
        const val ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
