# Quickstart: Validate the Local Data Layer

## Prerequisites

- JDK 21 (Gradle provisions Azul 21 per `gradle/gradle-daemon-jvm.properties`), Android SDK.
- Branch `feature/002-local-data-layer`.

## 1. Automated tests

```bash
./gradlew :shared:testAndroidHostTest
```

Expected: all tests in `shared/src/androidHostTest/.../data/` pass. Coverage by scenario:

| Test class | Verifies | Spec |
|------------|----------|------|
| `SchemaIntegrityTest` | duplicate phone, orphan attempt/session, second open session, second non-final attempt, bad phone format, real image ref, REJECTED without reason → all refused | FR-002, FR-015, FR-019, FR-020, FR-021, SC-004 |
| `SessionRepositoryTest` | sample login OK; 3→2→1→locked; correct PIN when locked refused; logout keeps user; switch account; `currentUser` emits on login/logout/status change | US1, US5, FR-005, FR-006, FR-025 |
| `RegistrationRepositoryTest` | invalid phone; resend < 30 s refused; wrong code tries; expired code; mismatched PIN; duplicate phone; success → NOT_VERIFIED, terms recorded, session open; PIN stored not readable | US2, FR-009–FR-011, SC-007 |
| `KycRepositoryTest` | start refused when verified; incomplete submit lists missing items (CI vs passport); outcomes by last digit 0/5/other; user status follows; history kept; second open attempt refused | US3, FR-013–FR-018, SC-003 |
| `SeedDataTest` | exactly 6 sample users with fixed ids; identical after 10 resets; status matches latest attempt; empty reset has 0 users; no data outside `+59170000xxx` / `@example.com` | US4, FR-022, FR-023, SC-002 |

## 2. Compile gates (same as the PR check)

```bash
./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin
```

Expected: success. Proves no `expect` declaration left the jvm/Desktop target without an `actual`.

## 3. Manual inspection (optional)

1. `./gradlew :shared:generateCommonMainQullqiDatabaseInterface` and review generated queries.
2. Sample credentials for M2/M3 screen work: any sample user, PIN `1234`, SMS code `123456`;
   document number ending in `0` → rejected, `5` → under review, other → approved.

## 4. Contract review (SC-005)

The M4+ developer reads [contracts/current-user.md](contracts/current-user.md) only and confirms
they can implement the "verified only" gate. Then set the spec header to
`**Contract**: Approved (YYYY-MM-DD, <approver>)`.
