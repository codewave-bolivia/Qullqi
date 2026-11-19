package com.codewave.qullqi

import com.codewave.qullqi.data.LocalDataReset
import com.codewave.qullqi.data.SqlAccountPreferenceRepository
import com.codewave.qullqi.data.SqlRegistrationRepository
import com.codewave.qullqi.data.SqlSessionRepository
import com.codewave.qullqi.data.remote.QullqiApi
import com.codewave.qullqi.data.remote.RemoteKycRepository
import com.codewave.qullqi.data.remote.RemoteRegistrationRepository
import com.codewave.qullqi.data.remote.RemoteSessionRepository
import com.codewave.qullqi.data.remote.TokenStore
import com.codewave.qullqi.data.remote.deviceId
import com.codewave.qullqi.db.QullqiDatabase
import io.ktor.client.engine.HttpClientEngine
import com.codewave.qullqi.domain.repository.AccountPreferenceRepository
import com.codewave.qullqi.domain.repository.CurrentUserSource
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.RegistrationRepository
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.security.BiometricAuthenticator
import com.codewave.qullqi.domain.security.NoBiometrics
import com.codewave.qullqi.domain.security.PinHasher
import com.codewave.qullqi.domain.session.AppLock
import com.codewave.qullqi.domain.system.Clock
import com.codewave.qullqi.domain.system.ProviderPage
import com.codewave.qullqi.domain.system.NoProviderPage
import com.codewave.qullqi.domain.system.IdGenerator
import com.codewave.qullqi.domain.system.OtpCodeGenerator
import kotlinx.coroutines.CoroutineDispatcher

/** Everything the shared flows need, built once per process by the platform host. */
data class AppDependencies(
    val session: SessionRepository,
    val registration: RegistrationRepository,
    val preferences: AccountPreferenceRepository,
    val kyc: KycRepository,
    val biometrics: BiometricAuthenticator,
    val appLock: AppLock,
    val clock: Clock,
    /** Debug builds only: lets the owner clear the device data from the splash (spec 004, FR-021). */
    val debugReset: LocalDataReset?,
    /** Opens the identity provider page (camera permission + WebView on Android, spec 005). */
    val providerPage: ProviderPage = NoProviderPage,
) {
    val currentUser: CurrentUserSource get() = session

    companion object {
        /** App wiring: the backend is authoritative (constitution v2.1.0, spec 004). */
        fun create(
            db: QullqiDatabase,
            clock: Clock,
            ids: IdGenerator,
            io: CoroutineDispatcher,
            debug: Boolean,
            httpEngine: HttpClientEngine,
            baseUrl: String,
            tokens: TokenStore,
            biometrics: BiometricAuthenticator = NoBiometrics,
        ): AppDependencies {
            val api = QullqiApi(httpEngine, baseUrl, debug)
            val deviceId: () -> String = { db.deviceId(ids) }
            val session = RemoteSessionRepository(db, api, tokens, deviceId, clock, io)
            return AppDependencies(
                session = session,
                registration = RemoteRegistrationRepository(api, session, tokens, deviceId),
                preferences = SqlAccountPreferenceRepository(db, clock, io),
                kyc = RemoteKycRepository(api),
                biometrics = biometrics,
                appLock = AppLock(clock),
                clock = clock,
                debugReset = if (debug) LocalDataReset(db, tokens) else null,
            )
        }

        /** In-memory repositories on SQLDelight, for ViewModel tests only (spec 004, T023). */
        fun createInMemory(
            db: QullqiDatabase,
            hasher: PinHasher,
            clock: Clock,
            ids: IdGenerator,
            otp: OtpCodeGenerator,
            io: CoroutineDispatcher,
            kyc: KycRepository,
            biometrics: BiometricAuthenticator = NoBiometrics,
        ) = AppDependencies(
            session = SqlSessionRepository(db, hasher, clock, io),
            registration = SqlRegistrationRepository(db, hasher, clock, ids, otp, io),
            preferences = SqlAccountPreferenceRepository(db, clock, io),
            kyc = kyc,
            biometrics = biometrics,
            appLock = AppLock(clock),
            clock = clock,
            debugReset = null,
        )
    }
}
