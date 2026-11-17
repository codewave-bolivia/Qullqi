# Research: Backend Integration for Access and Registration

## R1. HTTP client and JSON

- **Decision**: Ktor Client 3.x — `ktor-client-core`, `ktor-client-content-negotiation`,
  `ktor-client-auth`, `ktor-serialization-kotlinx-json` in `commonMain`; `ktor-client-okhttp` in
  `androidApp` (engine injected into `AppDependencies.create`); `ktor-client-mock` in
  `androidHostTest`. `kotlinx-serialization-json` + plugin `org.jetbrains.kotlin.plugin.serialization`
  (version = Kotlin version, 2.4.21).
- **Versions**: pin the latest **stable** Ktor 3.x and kotlinx-serialization-json 1.x that declare
  support for Kotlin 2.4 at task T001 (check Maven Central; no alpha/beta/RC). Record them in the
  catalog only.
- **Rationale**: approved by constitution v2.0.0; multiplatform; `MockEngine` gives host tests
  without a live backend; `auth` bearer provider handles 401 → refresh → retry with one refresh for
  concurrent requests (FR-007).
- **Engine in androidApp**: keeps `shared/androidMain` free of a network engine and lets the
  Builder add Darwin/Java later without touching shared code.
- **Alternatives**: OkHttp/Retrofit (Android-only, breaks Principle III); hand-written refresh
  mutex (more code).

## R2. Secure storage of session credentials

- **Decision**: interface `TokenStore` (commonMain): `refreshToken(userId)`, `saveRefreshToken(userId,
  token)`, `clear(userId)`, `clearAll()`. Android `KeystoreTokenStore` (shared/androidMain): AES-256-GCM
  key in `AndroidKeyStore` (alias `qullqi_tokens`, no user-auth requirement), ciphertext + IV
  Base64 in SharedPreferences `qullqi_secure`, key `rt_<userId>`. The **access token is never
  persisted**: it lives in memory (bearer provider) and is re-obtained by login or refresh.
- **Rationale**: no new dependency (`androidx.security-crypto` is deprecated); the plain
  preferences file contains only ciphertext (FR-005). Per-user keys let the fingerprint log in the
  account chosen on the login screen.
- **Alternatives**: SQLDelight column (forbidden by FR-005); EncryptedSharedPreferences
  (deprecated); DataStore (extra dependency for no gain).

## R3. Fingerprint login (FR-008)

- **Decision**: cold start with fingerprint = existing `BiometricPrompt` check (`BIOMETRIC_STRONG
  or WEAK`) succeeds → read the user's renewal credential → `POST /auth/refresh` → session.
  Rejected refresh (401/423) → `LoginResult.SessionExpired`: clear that user's credential, hide
  the fingerprint key until the next PIN login, show "Ingresa tu PIN para continuar".
- **Ceiling (documented, `ponytail:` comment in `KeystoreTokenStore`)**: the Keystore key is not
  bound to biometric authentication, so the fingerprint is an app-level gate, not a cryptographic
  one; a rooted device could read the credential. Upgrade path: a second key with
  `setUserAuthenticationRequired(true)` + `BiometricPrompt.CryptoObject` to decrypt only on cold
  start (re-encrypt on each rotation needs a prompt → keep the decrypted value in memory for the
  session). Deferred: it forces a prompt on every rotation unless carefully designed, and the
  backend already bounds the credential (30 days, rotation, reuse detection, device binding).
- **Alternatives**: store the PIN (forbidden FR-025); biometric-bound key now (above).

## R4. Device identifier

- **Decision**: table `device(slot INTEGER PK CHECK (slot = 1), device_id TEXT NOT NULL)`; UUID
  from the existing `IdGenerator` created on first use (`INSERT OR IGNORE`). Cleared only by app
  data removal or the debug reset.
- **Rationale**: device-only data belongs in SQLDelight (constitution I); a single-row table
  mirrors `current_session`.

## R5. Schema migration (installed spec 003 builds)

- **Decision**: schema v1 → v2 with `1.sqm`:
  `ALTER TABLE user ADD COLUMN first_name TEXT; … last_name_paternal TEXT; … last_name_maternal TEXT;`
  `CREATE TABLE device (...)`; `DELETE FROM phone_confirmation; DELETE FROM user;` (cascades
  credential, session, terms, preferences, KYC attempts). The local accounts of spec 003 builds are
  sample/local-only and do not exist in the backend (spec edge case).
- New columns are nullable (SQLite cannot add NOT NULL without default); remote rows always fill
  them; `full_name` stays NOT NULL as the display name ("first + paternal").
- **Verification**: before editing `.sq`, generate the v1 snapshot
  (`schemaOutputDirectory = shared/src/commonMain/sqldelight/databases`,
  `./gradlew :shared:generateCommonMainQullqiDatabaseSchema` → `1.db`), commit it, then
  `verifySqlDelightMigration = true` and `./gradlew :shared:verifyCommonMainQullqiDatabaseMigration`.
- **Fresh installs**: `AndroidDatabaseFactory` uses `QullqiDatabase.Schema` (no seed).
  `SeededSchema` stays for tests only (FR-021).

## R6. Backend address and transport

- **Decision**: `androidApp` `buildConfigField("String", "API_BASE_URL", …)` from Gradle
  properties: `qullqi.api.url.debug` (default `http://10.0.2.2:8080/api/v1/`, emulator) and
  `qullqi.api.url.release` (default `https://api.qullqi.invalid/api/v1/` until production exists:
  the CD pre-release then shows the connection message, never cleartext). Physical device in debug:
  set `qullqi.api.url.debug=http://<lan-ip>:8080/api/v1/` in `~/.gradle/gradle.properties`.
- Cleartext: `androidApp/src/debug/AndroidManifest.xml` with
  `android:usesCleartextTraffic="true"`; main manifest gains `INTERNET` permission.
- Timeouts: connect 10 s, request 15 s. No `HttpRequestRetry` plugin (FR-004).

## R7. Error mapping

- One `ApiError(code, httpStatus, details)` parsed from `{error:{code,message,details}}`;
  `IOException`/timeouts → `Unavailable`. Repositories map codes to existing domain results
  ([contracts/api-mapping.md](contracts/api-mapping.md)); the backend's `message` is **not** shown
  (UI keeps its own Spanish texts, FR-003), only used in debug logs without request bodies.
- Logging: no Ktor `Logging` plugin in release; in debug only `LogLevel.INFO` (URL + status, no
  headers/bodies) so tokens, PINs and codes never reach logs.

## R8. Registration names and the phone proof

- Step 3: first name, paternal surname, maternal surname (optional), email, terms. Display name
  for UI and `CurrentUser.displayName` = `"$firstName $lastNamePaternal"` (FR-018).
- `phoneVerifiedToken` from `verify-otp` lives only in `RemoteRegistrationRepository` memory, keyed
  by phone + purpose; `register` 401 → `RegistrationResult.PhoneNotConfirmed` → ViewModel returns
  to the code step keeping the draft (FR-012).
- Already-registered numbers: backend answers 200 without SMS; code step shows the link "¿Ya tienes
  cuenta? Inicia sesión" (FR-011) → `LoginPhone` prefilled with the number.

## R9. Current user cache

- `GET /me` → upsert into `user` (id, names, `full_name`, phone, email, verification) and
  `current_session`; `currentUser` Flow keeps reading the SQL cache, so offline shows the last
  value (FR-017) and M4's `CurrentUserSource` is unchanged.
- Refresh points: after login/register/biometric login, and `MainActivity.onStart`
  (`deps.session.refreshCurrentUser()`), ignoring `Unavailable`.
- `accountStatus = LOCKED` from login 423 → `pin_attempts_left = 0` in the cache so the account
  list shows it locked; successful login/reset → 3.
