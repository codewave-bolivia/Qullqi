package com.codewave.qullqi.domain.system

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Current time in epoch milliseconds; injected so tests control expiry and resend windows. */
fun interface Clock {
    fun nowMillis(): Long
}

fun interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
object UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}

fun interface OtpCodeGenerator {
    fun newCode(): String
}

/** No SMS without a backend: sample mode always issues this documented code (spec 002, FR-011). */
object SampleOtpCodeGenerator : OtpCodeGenerator {
    const val CODE = "123456"
    override fun newCode(): String = CODE
}
