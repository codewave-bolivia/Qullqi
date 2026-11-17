# Data Model: Identity Verification with Persona (M3)

## Domain (`domain/model/Kyc.kt`, replaced — shared area)

```kotlin
enum class DocumentType { CI, PASSPORT }
enum class KycStatus { CREATED, PENDING, APPROVED, DECLINED }
enum class DeclineReason { DATA_MISMATCH, DOCUMENT, IDENTITY, OTHER }
data class SimpleDate(val year: Int, val month: Int, val day: Int)   // ISO "YYYY-MM-DD" on the wire
data class Verification(
    val status: KycStatus,
    val pageUrl: String?,            // only while CREATED
    val declineReason: DeclineReason?,
)
```

Rules (domain functions, tested):
- `documentNumberValid(n)`: `^[A-Za-z0-9]{5,15}$` after trim (FR-003).
- `SimpleDate.parseDigits("15041992")` → valid calendar date or null (month 1–12, day ≤ month
  length, Feb 29 only in leap years).
- `isAdult(birth, today)`: birth < today and age ≥ 18 (birthday reached).

## Local database

Schema v3 (`2.sqm`): `kyc_attempt` (+ index `kyc_attempt_one_open`, `kyc_attempt_history`,
trigger `kyc_attempt_final_sets_user_status`) dropped. No new tables: the verification lives in the
backend; `user.verification` is refreshed through `/me` (spec 004).

## UI state (`KycViewModel`, not persisted)

| Field | Rules |
|---|---|
| route | `Intro · Document · Waiting · Result` (own back stack inside `kycIntro`) |
| privacyAccepted | required to leave Intro (FR-001) |
| pending | `Verification?` read on entry (FR-002) |
| documentType | `CI` default |
| documentNumber | `^[A-Za-z0-9]{5,15}$` |
| birthDigits | 0–8 digits shown `DD/MM/AAAA`; valid date ∧ adult |
| busy | true while a call runs; actions disabled (spec 004 FR-003a) |
| cameraDenied | `null` · `Ask` · `Settings` (permanent): Document shows "Necesitamos tu cámara para verificarte" + "Permitir cámara" (FR-005b) |
| waitingPhase | `InProvider` (tab open) · `Checking(elapsedS)` · `NotFinished` · `Offline` |
| result | `Approved · InReview · Rejected(reason) · Refused(support)` |

### Transitions

```text
enter ─GET─▶ none/DECLINED ─▶ Intro ─accept─▶ Document ─valid─▶ POST ─▶ open tab ─▶ Waiting(InProvider)
              CREATED ─▶ Intro(resume) ─"Continuar verificación"─▶ open tab(GET url) ─▶ Waiting
              PENDING ─▶ Result(InReview)      APPROVED ─▶ Result(Approved)
Waiting(InProvider) ─resume─▶ Checking ─GET every 5 s ≤ 60 s─▶ APPROVED/DECLINED ─▶ Result
                                          CREATED ─▶ NotFinished ─"Volver"─▶ tab
                                          still PENDING at 60 s ─▶ Result(InReview)
Result(Rejected) ─"Intentar de nuevo"─▶ Document (empty)
Result(Approved | InReview) ─"Ir al inicio"─▶ refresh /me ─▶ onDone()
```
