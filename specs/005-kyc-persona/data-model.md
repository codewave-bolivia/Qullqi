# Data Model: Identity Verification with Persona (M3)

## Domain (`domain/model/Kyc.kt`, replaced — shared area)

```kotlin
enum class KycStatus { CREATED, PENDING, APPROVED, DECLINED }
enum class DeclineReason { DATA_MISMATCH, DOCUMENT, IDENTITY, UNDERAGE, DOCUMENT_IN_USE, OTHER }
data class Verification(
    val status: KycStatus,
    val pageUrl: String?,            // only while CREATED
    val declineReason: DeclineReason?,
)
```

No document data in the app (FR-003, 2026-11-17): type, number and birth date are read by the
provider and checked by the backend (age ≥ 18, number unique) on the verified values.


## Local database

Schema v3 (`2.sqm`): `kyc_attempt` (+ index `kyc_attempt_one_open`, `kyc_attempt_history`,
trigger `kyc_attempt_final_sets_user_status`) dropped. No new tables: the verification lives in the
backend; `user.verification` is refreshed through `/me` (spec 004).

## UI state (`KycViewModel`, not persisted)

| Field | Rules |
|---|---|
| route | `Intro · Waiting · Result` (own back stack inside `kycIntro`) |
| privacyAccepted | required to leave Intro (FR-001) |
| pending | `Verification?` read on entry (FR-002) |
| busy | true while a call runs; actions disabled (spec 004 FR-003a) |
| cameraDenied | `null` · `Ask` · `Settings` (permanent): Intro shows "Necesitamos tu cámara para verificarte" + "Permitir cámara" (FR-005b) |
| waitingPhase | `InProvider` (tab open) · `Checking(elapsedS)` · `NotFinished` · `Offline` |
| result | `Approved · InReview · Rejected(reason) · Refused(support)` |

### Transitions

```text
enter ─GET─▶ none/DECLINED ─▶ Intro ─accept + Continuar─▶ POST (no body) ─▶ open WebView ─▶ Waiting(InProvider)
              CREATED ─▶ Intro(resume) ─"Continuar verificación"─▶ open tab(GET url) ─▶ Waiting
              PENDING ─▶ Result(InReview)      APPROVED ─▶ Result(Approved)
Waiting(InProvider) ─resume─▶ Checking ─GET every 5 s ≤ 60 s─▶ APPROVED/DECLINED ─▶ Result
                                          CREATED ─▶ NotFinished ─"Volver"─▶ tab
                                          still PENDING at 60 s ─▶ Result(InReview)
Result(Rejected) ─"Intentar de nuevo"─▶ Intro (new verification)
Result(Approved | InReview) ─"Ir al inicio"─▶ refresh /me ─▶ onDone()
```
