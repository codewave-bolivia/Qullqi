# Quickstart: Validate Access and Registration

## Prerequisites

- Physical Android device (API 24+; API 31+ to see the system launch screen behaviour) with a
  fingerprint enrolled, USB debugging on.
- Branch `feature/003-access-registration`; Inter + Phosphor assets added (approved download).

## 1. Automated

```bash
./gradlew :shared:testAndroidHostTest
./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin
```

Expected: all green. Unit tests cover [contracts/viewmodels.md](contracts/viewmodels.md) invariants,
AppLock timing (4:59 vs 5:01 with a fake clock) and US1–US6 scenarios that need no real sensor.

## 2. On the device

```bash
./gradlew :androidApp:installDebug
```

Sample data: PIN `1234`, SMS code `123456` (CLAUDE.md §14).

| # | Scenario | Expected | Spec |
|---|----------|----------|------|
| 1 | Cold start | Nocturne launch screen with Qullqi icon → splash matching the reference `is.splash` block (left-aligned wordmark, underline, tagline, glow, bottom bar; desktop render: `assets/splash-render.png`), no white frame → login "Hola de nuevo, Andrés" (no logins yet → first account by name) | US1, FR-001a |
| 2 | PIN `1234` | "Ingresando…" → "Activar huella" once → veil → MainActivity placeholder | US2, US5-5, FR-032 |
| 3 | Back in MainActivity | app closes (does not show login) | FR-033 |
| 4 | Reopen from launcher | launch screen → splash → login again (cold start rule) | FR-002a |
| 5 | Home, wait 2 min, return | MainActivity as left, no PIN | FR-002b |
| 6 | Home, wait > 5 min, return | unlock screen (fingerprint/PIN), then same screen as left | FR-002b, SC-007 |
| 7 | Recents screen after backgrounding (API 33+) | no MainActivity content in the snapshot | Edge case |
| 8 | "Verificar" in MainActivity | veil → AuthActivity KYC placeholder → done → back to MainActivity | FR-020 |
| 9 | "Cerrar sesión" | confirm sheet → veil → login for same account; back exits the app | FR-021, FR-033 |
| 10 | Wrong PIN ×3 on another account → Recuperar PIN | locked → code `123456` → new PIN ×2 → toast → login works | US2, US4 |
| 11 | Cambiar de cuenta → Agregar otra cuenta | registration 1/4…4/4 → "Cuenta creada" · Sin verificar | US3 |
| 12 | Rotate / split-screen in registration step 3 | data kept, no horizontal overflow | FR-014, FR-022 |
| 13 | Debug: long-press wordmark → "Reiniciar datos sin ejemplos", restart | welcome screen | US1-2 (research R13) |
| 14 | Compare every screen with `Qullqi Pay Auth.dc.html` | colors, type, outlined primary, boxes, keypad match | FR-026–FR-030 |

## 3. Desktop

`./gradlew :desktopApp:run` → "Qullqi aún no está disponible en esta plataforma" (no crash).
