package com.codewave.qullqi.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// Wire format of the Qullqi backend (BackendQullpi docs/integration-android/endpoints.md).
// Stays in the data layer; repositories map it to domain results (constitution I).

internal val QullqiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable internal data class Envelope<T>(val data: T? = null)

@Serializable internal data class ErrorBody(val error: ErrorDetail)

@Serializable internal data class ErrorDetail(val code: String, val message: String? = null, val details: JsonObject? = null)

@Serializable internal data class RequestOtpBody(val phoneNumber: String, val purpose: String)

@Serializable internal data class VerifyOtpBody(val phoneNumber: String, val code: String, val purpose: String)

@Serializable internal data class PhoneTokenDto(val phoneVerifiedToken: String)

@Serializable
internal data class RegisterBody(
    val firstName: String,
    val lastNamePaternal: String,
    val lastNameMaternal: String? = null,
    val email: String,
    val pin: String,
    val deviceId: String,
    val termsVersion: String,
)

@Serializable internal data class LoginBody(val phoneNumber: String, val pin: String, val deviceId: String)

@Serializable internal data class RefreshBody(val refreshToken: String, val deviceId: String)

@Serializable internal data class LogoutBody(val deviceId: String)

@Serializable internal data class PinResetBody(val newPin: String, val deviceId: String)

@Serializable internal data class TokensDto(val token: String, val refreshToken: String, val expiresIn: Int = 900)

@Serializable
internal data class MeDto(
    val userId: String,
    val phoneNumber: String,
    val email: String,
    val firstName: String,
    val lastNamePaternal: String,
    val lastNameMaternal: String? = null,
    val verificationStatus: String,
    val accountStatus: String? = null,
)

/** Integer from an error's `details`, e.g. `attempts_left`, `tries_left`, `retry_after_seconds`. */
internal fun JsonObject?.int(key: String): Int? = this?.get(key)?.jsonPrimitive?.intOrNull
