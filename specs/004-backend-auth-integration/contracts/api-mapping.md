# Contract: Backend endpoint → repository → domain result

Source of the API: `BackendQullpi/docs/integration-android/endpoints.md` (spec 006 level).
Base `API_BASE_URL` = `…/api/v1/`. Success envelope `{success, message, status, data}`; error
`{error:{code, message, details}}`. Any I/O error, timeout or `5xx`/`internal_error` → `Unavailable`.
Unknown error codes → `Unavailable` (generic message).

| Repository method | Request | Response → result |
|---|---|---|
| `requestCode(phone, purpose)` | `POST auth/request-otp {phoneNumber, purpose: PHONE_VERIFICATION\|PIN_RESET}` | 200 → `Sent` · 429 `too_many_requests {retry_after_seconds}` → `TooSoon(s*1000)` · 422 → `InvalidPhone` |
| `confirmCode(phone, code, purpose)` | `POST auth/verify-otp {phoneNumber, code, purpose}` | 200 `{phoneVerifiedToken}` → keep in memory, `Confirmed` · 400 `wrong_code {tries_left}` → `Wrong(n)` · 410 → `Expired` · 404 → `NoCode` |
| `register(form)` | `POST auth/register` `Bearer <phoneVerifiedToken>` `{displayName, email, pin, deviceId, termsVersion}` | 201 `{token, refreshToken, expiresIn}` → save tokens, `GET me`, cache, open session, `Created(id)` · 401 → `PhoneNotConfirmed` · 409 `phone_registered` → `PhoneAlreadyRegistered` · 422 `{fields}` → `Invalid(mapped fields)` |
| `login(userId, pin)` / `loginWithPhone(phone, pin)` | `POST auth/login {phoneNumber, pin, deviceId}` | 200 tokens → save, `GET me`, cache, attempts = 3, `Success` · 401 `invalid_pin {attempts_left}` → cache attempts, `WrongPin(n)` · 423 → attempts = 0, `Locked` · 422 → `UnknownUser` |
| `loginWithBiometrics(userId)` | `POST auth/refresh {refreshToken, deviceId}` (no stored token → `SessionExpired`) | 200 → rotate stored token, `GET me`, `Success` · 401/423 → clear token, `SessionExpired` |
| automatic renewal (bearer `refreshTokens`) | `POST auth/refresh` (marked as refresh request) | 200 → rotate · 401 → clear token, close local session → UI goes to login (US3-3) |
| `refreshCurrentUser()` | `GET me` `Bearer <access>` | 200 → upsert cache · 401 (after failed refresh) → session closed · `Unavailable` → ignore |
| `logout()` | `POST auth/logout {deviceId}` `Bearer <access>` | 204/any/`Unavailable` → always clear token + bearer cache + `current_session` (FR-016) |
| `resetPin(phone, newPin)` | `POST auth/pin/reset` `Bearer <phoneVerifiedToken PIN_RESET>` `{newPin, deviceId}` | 200 tokens → discard (UI returns to login, spec 003 flow), cache attempts = 3, clear stored token of that user, `Success` · 401 → `PhoneNotConfirmed` · 422 → `InvalidPin` |

`/me` → cache mapping: `id` or `userId` → id; `full_name` = `displayName`, else `firstName
lastNamePaternal`, else "Cliente" (never blank: table CHECK); legal names `firstName`,
`lastNamePaternal`, `lastNameMaternal` stored as-is (null until KYC); `phoneNumber` normalized to
`+591…` (else the login phone); `email` lowercased; `verificationStatus → verification` (unknown →
`NOT_VERIFIED`).

`details` keys: both spellings accepted (`attempts_left`/`attemptsLeft`, `tries_left`/`triesLeft`,
`retry_after_seconds`/`retryAfterSeconds`); cached attempts clamped to 0–3.

Registration `422.fields` mapping: `displayName → NAME`, `email → EMAIL`, `pin → PIN`; other →
`Unavailable`.
