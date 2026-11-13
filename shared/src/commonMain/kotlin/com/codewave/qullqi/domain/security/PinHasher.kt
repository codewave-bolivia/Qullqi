package com.codewave.qullqi.domain.security

/** Protected, verifiable representation of a PIN. The PIN itself is never stored (FR-004). */
data class PinHash(val hashB64: String, val saltB64: String, val iterations: Int)

/** Platform-provided: Android implements it; iOS/Desktop implementations come from the Builder. */
interface PinHasher {
    fun hash(pin: String): PinHash
    fun verify(pin: String, stored: PinHash): Boolean
}
