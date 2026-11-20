package com.codewave.qullqi.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
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
    val displayName: String,
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

/** `/me` body. The backend sends `id` while its doc says `userId`: both are accepted (spec 004, T056). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class MeDto(
    @JsonNames("userId", "id") val userId: String,
    val phoneNumber: String,
    val email: String,
    /** Always present since backend f102c1d; older backends omit it. */
    val displayName: String? = null,
    /** Legal names: null until identity verification is approved (filled by Persona). */
    val firstName: String? = null,
    val lastNamePaternal: String? = null,
    val lastNameMaternal: String? = null,
    val verificationStatus: String,
    val accountStatus: String? = null,
)

@Serializable
internal data class KycDto(val kycStatus: String, val hostedUrl: String? = null, val declineReason: String? = null)

/** Wrapper so `GET my/kyc` can return "no verification" (`data: null`) as a success. */
internal data class KycOrNone(val dto: KycDto?)

/**
 * Integer from an error's `details`: the first of [keys] present. The backend mixes spellings
 * (`attemptsLeft` vs documented `attempts_left`), so callers pass both (spec 004, T057).
 */
internal fun JsonObject?.int(vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { this?.get(it)?.jsonPrimitive?.intOrNull }
