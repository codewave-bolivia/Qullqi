# Quickstart: Backend Integration for Access and Registration

## Prerequisites

- Backend running locally in the `dev` profile (`BackendQullpi`, `http://localhost:8080`,
  fixed OTP `123456`, users `+59170000001…6`, PIN `1234`).
- Emulator: default `API_BASE_URL` `http://10.0.2.2:8080/api/v1/`.
  Physical device: in `~/.gradle/gradle.properties`
  `qullqi.api.url.debug=http://<lan-ip-of-pc>:8080/api/v1/` (same Wi-Fi).

## Automated checks

```bash
./gradlew :shared:verifyCommonMainQullqiDatabaseMigration   # v1 → v2 migration
./gradlew :shared:testAndroidHostTest                       # remote repos (MockEngine) + ViewModels
./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin
./gradlew :androidApp:assembleDebug                         # includes portrait guard
```

## Manual scenarios (debug build on device)

| # | Steps | Expected |
|---|---|---|
| 1 | Fresh install → splash | Welcome (no sample accounts) |
| 2 | "Ya tengo cuenta" → `70000001` → PIN `1234` | Main area: "Lucía …", Verificado (US1) |
| 3 | Login `+59170000002` with PIN `9999` twice | "Te quedan 2/1 intentos" from backend |
| 4 | Third wrong PIN | Locked state, only "Recuperar PIN" |
| 5 | Recover: code `123456`, new PIN twice | "PIN actualizado", login with the new PIN works (US5) |
| 6 | Register new `7xxxxxxx`, code `123456`, names (maternal empty), email, terms, PIN | "Cuenta creada", main area with first name (US2) |
| 7 | Register with `70000001` | No SMS; code step link "¿Ya tienes cuenta? Inicia sesión"; finishing gives "ya registrado" → login |
| 8 | Enable fingerprint, kill app, reopen, fingerprint | Main area without PIN (US3) |
| 9 | Keep app open > 15 min, navigate | No PIN asked (renewal) |
| 10 | Logout, then (adb) replay old refresh | Backend 401 (US4) |
| 11 | Airplane mode on any step | "Sin conexión. Intenta de nuevo."; no attempt used |
| 12 | Upgrade a spec 003 build in place | Opens, reaches welcome; no crash (SC-007) |
| 13 | `adb shell run-as com.codewave.qullqi` → inspect `databases/qullqi.db`, `shared_prefs/` | No PIN, code or token in clear (SC-004) |

## Release note

Release builds use `qullqi.api.url.release` (default `https://api.qullqi.invalid/…`): the develop
pre-release APK shows the connection message until a production URL exists.
PIN recovery must not reach a release before backend item S2 is fixed, and registration not before
S1 is fixed (`docs/backend-requirements/endpoints-change-request.md`).
