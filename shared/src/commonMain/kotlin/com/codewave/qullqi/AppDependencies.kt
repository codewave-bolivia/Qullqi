package com.codewave.qullqi

import com.codewave.qullqi.data.LocalDataReset
import com.codewave.qullqi.data.MockKycProcessor
import com.codewave.qullqi.data.SqlAccountPreferenceRepository
import com.codewave.qullqi.data.SqlKycRepository
import com.codewave.qullqi.data.SqlRegistrationRepository
import com.codewave.qullqi.data.SqlSessionRepository
import com.codewave.qullqi.db.QullqiDatabase
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
    /** Debug builds only: lets the owner reset sample data from the splash (research R13). */
    val debugReset: LocalDataReset?,
) {
    val currentUser: CurrentUserSource get() = session

    companion object {
        fun create(
            db: QullqiDatabase,
            hasher: PinHasher,
            clock: Clock,
            ids: IdGenerator,
            otp: OtpCodeGenerator,
            io: CoroutineDispatcher,
            debug: Boolean,
            biometrics: BiometricAuthenticator = NoBiometrics,
        ) = AppDependencies(
            session = SqlSessionRepository(db, hasher, clock, io),
            registration = SqlRegistrationRepository(db, hasher, clock, ids, otp, io),
            preferences = SqlAccountPreferenceRepository(db, clock, io),
            kyc = SqlKycRepository(db, clock, ids, MockKycProcessor(), io),
            biometrics = biometrics,
            appLock = AppLock(clock),
            clock = clock,
            debugReset = if (debug) LocalDataReset(db) else null,
        )
    }
}
