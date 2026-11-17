package com.codewave.qullqi.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.clearAuthTokens
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Outcome of one backend call, before mapping to domain results (contracts/api-mapping.md). */
internal sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Fail(val code: String, val status: Int, val details: JsonObject?) : ApiResult<Nothing>
    /** I/O error, timeout, 5xx or unreadable body: nothing is known to have happened (FR-003). */
    data object Unavailable : ApiResult<Nothing>
}

/**
 * HTTP access to the backend (spec 004, research R1/R7). Public auth calls skip the bearer plugin;
 * `/me` and `/auth/logout` use the in-memory access token, renewed once on 401 through [renew]
 * (Ktor's bearer provider merges concurrent renewals, FR-007). No retry plugin (FR-004).
 */
class QullqiApi(engine: HttpClientEngine, baseUrl: String, debug: Boolean) {

    /** Access token of the open session; memory only (FR-005). */
    internal var access: String? = null

    /** Renews the session and returns the new access token, or null when it cannot (set by the session repository). */
    internal var renew: (suspend () -> String?)? = null

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(QullqiJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
        install(Auth) {
            bearer {
                loadTokens { access?.let { BearerTokens(it, null) } }
                refreshTokens { renew?.invoke()?.let { BearerTokens(it, null) } }
            }
        }
        // Debug only: method, URL and status. Never headers or bodies (tokens, PIN, codes; SC-004).
        if (debug) install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    /** Forgets the cached bearer so the next call reads [access] again. */
    internal fun clearBearer() {
        client.clearAuthTokens()
    }

    internal suspend fun requestOtp(body: RequestOtpBody): ApiResult<Unit> = call { post("auth/request-otp") { skipSession(); setBody(body) } }

    internal suspend fun verifyOtp(body: VerifyOtpBody): ApiResult<PhoneTokenDto> = call { post("auth/verify-otp") { skipSession(); setBody(body) } }

    internal suspend fun register(phoneToken: String, body: RegisterBody): ApiResult<TokensDto> =
        call { post("auth/register") { skipSession(); bearerAuth(phoneToken); setBody(body) } }

    internal suspend fun login(body: LoginBody): ApiResult<TokensDto> = call { post("auth/login") { skipSession(); setBody(body) } }

    internal suspend fun refresh(body: RefreshBody): ApiResult<TokensDto> = call { post("auth/refresh") { skipSession(); setBody(body) } }

    internal suspend fun pinReset(phoneToken: String, body: PinResetBody): ApiResult<TokensDto> =
        call { post("auth/pin/reset") { skipSession(); bearerAuth(phoneToken); setBody(body) } }

    internal suspend fun logout(body: LogoutBody): ApiResult<Unit> = call { post("auth/logout") { setBody(body) } }

    internal suspend fun me(): ApiResult<MeDto> = call { get("me") }

    /** `GET my/kyc`: `data: null` means no verification yet → `Ok(null)` (spec 005). */
    internal suspend fun kycCurrent(): ApiResult<KycDto?> = call<KycOrNone> { get("my/kyc") }.let {
        if (it is ApiResult.Ok) ApiResult.Ok(it.value.dto) else it as ApiResult<Nothing>
    }

    internal suspend fun kycStart(body: KycStartBody): ApiResult<KycDto> = call { post("my/kyc") { setBody(body) } }

    /** Public endpoints: the bearer plugin neither adds the session token nor tries to renew on 401. */
    private fun HttpRequestBuilder.skipSession() {
        attributes.put(AuthCircuitBreaker, Unit)
    }

    private suspend inline fun <reified T> call(request: HttpClient.() -> HttpResponse): ApiResult<T> = try {
        val response = client.request()
        val status = response.status.value
        when {
            response.status.isSuccess() -> {
                @Suppress("UNCHECKED_CAST")
                if (T::class == Unit::class) ApiResult.Ok(Unit as T)
                else if (T::class == KycOrNone::class) ApiResult.Ok(KycOrNone(response.body<Envelope<KycDto>>().data) as T)
                else response.body<Envelope<T>>().data?.let { ApiResult.Ok(it) } ?: ApiResult.Unavailable
            }
            status >= 500 -> ApiResult.Unavailable
            else -> {
                val error = runCatching { response.body<ErrorBody>().error }.getOrNull()
                ApiResult.Fail(error?.code.orEmpty(), status, error?.details)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Unavailable
    }
}
