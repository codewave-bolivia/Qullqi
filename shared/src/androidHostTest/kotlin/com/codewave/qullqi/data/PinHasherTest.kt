package com.codewave.qullqi.data

import com.codewave.qullqi.domain.security.PinHash
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PinHasherTest {
    private val hasher = Pbkdf2PinHasher()

    /** Seed.sq hashes were generated offline; they must verify with the Android hasher. */
    @Test fun seedHashMatchesAndroidHasher() {
        val seed = PinHash("VRNzRXffI0GsPDHEjulVUPzojJ6+ZUFCvlkA/t+X+ec=", "Fmh9rPjcxeuh0drNcorTsQ==", 120_000)
        assertTrue(hasher.verify("1234", seed))
        assertFalse(hasher.verify("1235", seed))
    }

    @Test fun hashIsSaltedAndNotThePin() {
        val a = hasher.hash("1234")
        val b = hasher.hash("1234")
        assertNotEquals(a.saltB64, b.saltB64)
        assertNotEquals(a.hashB64, b.hashB64)
        assertFalse(a.hashB64.contains("1234"))
        assertTrue(hasher.verify("1234", a))
    }
}
