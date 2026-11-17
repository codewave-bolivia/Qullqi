# Contract: KYC endpoints → domain results

Source: `BackendQullpi/docs/integration-android/endpoints.md` (commit `830814e`). Bearer session
token (spec 004: renewal on 401, `sessionExpired` on rejection). I/O, timeout, 5xx, unknown →
`Unavailable`.

| Method | Request | Response → result |
|---|---|---|
| `current()` | `GET my/kyc` | 200 `data: null` → `Found(null)` · 200 `{kycStatus, hostedUrl?, declineReason?}` → `Found(Verification)` (unknown status → `PENDING`) · other → `Unavailable` |
| `start(type, number, birth)` | `POST my/kyc {ciType: "CI"\|"PASSPORT", ciNumber, birthDate: "YYYY-MM-DD"}` | 200 `{hostedUrl}` → `Started(hostedUrl)` (status ignored, backend C1) · 409 `kyc_already_verified` → `AlreadyVerified` · 409 `kyc_in_progress` → `InProgress` · 409 `ci_taken` → `DocumentTaken` · 403 `kyc_rejected` → `Refused` · 422 `fields` (`ciNumber`→NUMBER, `birthDate`→BIRTH_DATE, `ciType`→TYPE; none → BIRTH_DATE when message says age) → `Invalid` · 200 without `hostedUrl` → `Unavailable` |

`declineReason` → `DeclineReason`: `data_mismatch` → DATA_MISMATCH; `document_unreadable`,
`document_expired` → DOCUMENT; `identity_not_verified`, `selfie_mismatch` → IDENTITY; else OTHER.

The page address is never stored or logged (debug logging prints URL paths only for our backend
requests; the provider URL is not requested through Ktor).
