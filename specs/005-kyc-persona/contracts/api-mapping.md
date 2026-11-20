# Contract: KYC endpoints → domain results

Source: `BackendQullpi/docs/integration-android/endpoints.md` (commit `830814e`). Bearer session
token (spec 004: renewal on 401, `sessionExpired` on rejection). I/O, timeout, 5xx, unknown →
`Unavailable`.

| Method | Request | Response → result |
|---|---|---|
| `current()` | `GET my/kyc` | 200 `data: null` → `Found(null)` · 200 `{kycStatus, hostedUrl?, declineReason?}` → `Found(Verification)` (unknown status → `PENDING`) · other → `Unavailable` |
| `start()` | `POST my/kyc` (no body) | 200 `{hostedUrl}` → `Started(hostedUrl)` · 409 `kyc_already_verified` → `AlreadyVerified` · 409 `kyc_in_progress` → `InProgress` · 403 `kyc_rejected` → `Refused` · 200 without `hostedUrl` / other → `Unavailable` |

`declineReason` → `DeclineReason`: `data_mismatch` → DATA_MISMATCH; `document_unreadable`,
`document_expired` → DOCUMENT; `identity_not_verified`, `selfie_mismatch` → IDENTITY; `underage` → UNDERAGE; `document_in_use` → DOCUMENT_IN_USE; else OTHER.

The page address is never stored or logged (debug logging prints URL paths only for our backend
requests; the provider URL is not requested through Ktor).
