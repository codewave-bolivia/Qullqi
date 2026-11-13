# Quickstart: Validate Access and Registration

## Prerequisites

- Physical Android device (API 24+) with a fingerprint enrolled, USB debugging on.
- Branch `feature/003-access-registration`; Inter + Phosphor assets added (approved download).

## 1. Automated

```bash
./gradlew :shared:testAndroidHostTest          # spec 002 tests + M2 ViewModel tests
./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin
```

Expected: all green. ViewModel tests cover [contracts/viewmodels.md](contracts/viewmodels.md)
invariants and US1–US6 acceptance scenarios that do not need a real sensor.

## 2. On the device

```bash
./gradlew :androidApp:installDebug
```

Sample data: PIN `1234`, SMS code `123456` (CLAUDE.md §14).

| # | Scenario | Expected | Spec |
|---|----------|----------|------|
| 1 | Fresh install, open | splash ≈1.9 s → login "Hola de nuevo, Lucía" (first by name) | US1 |
| 2 | PIN `1234` | "Ingresando…" → "Activar huella" sheet once → main placeholder | US2, US5-5 |
| 3 | Wrong PIN ×3 on another account | shake, "Te quedan 2/1 intentos", then locked | US2 |
| 4 | Recover PIN for the locked account | code `123456`, new PIN ×2 → toast → login works | US4 |
| 5 | Enable huella, logout, fingerprint key | enters without PIN; cancel prompt → no attempt used | US5 |
| 6 | Cambiar de cuenta → Agregar otra cuenta | registration 1/4…4/4 → "Cuenta creada" · Sin verificar | US3 |
| 7 | Rotate / split-screen during step 3 | data kept, no horizontal overflow | FR-014, FR-022 |
| 8 | Main placeholder → Verificar | KYC placeholder | US6 |
| 9 | Compare each screen with `Qullqi Pay Auth.dc.html` side by side | colors, type, outlined primary, boxes, keypad match | FR-026–FR-030 |

Empty-device state: clear app data (Settings → Apps → Qullqi → Storage) is **not** enough to reach
"welcome" because sample data is re-seeded; use a debug action or uninstall + test the welcome
route via the ViewModel test (`StartViewModel` with `withSamples = false`).

## 3. Desktop (unavailable screen)

`./gradlew :desktopApp:run` → "Qullqi aún no está disponible en esta plataforma" (no crash).
