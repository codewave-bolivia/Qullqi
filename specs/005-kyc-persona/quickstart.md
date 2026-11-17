# Quickstart: Identity Verification with Persona (M3)

## Prerequisites

- Spec 004 integrated; local dev backend with Persona **sandbox** credentials configured
  (backend side) and the redirect `qullqi://kyc/result`.
- Debug build on the device (`qullqi.api.url.debug` pointing to the PC).

## Automated checks

```bash
./gradlew :shared:verifyCommonMainQullqiDatabaseMigration   # v2 → v3 (drop kyc_attempt)
./gradlew :shared:testAndroidHostTest                       # RemoteKycRepository, KycViewModel (polling), domain dates
./gradlew :androidApp:assembleDebug                         # portrait guard includes KycReturnActivity
```

## Manual scenarios (device)

| # | Steps | Expected |
|---|---|---|
| 1 | Not verified user → "Verificar" → intro | Requirements + privacy; "Continuar" disabled until accepted |
| 2 | CI, `1234567`, birth `15/04/1992` → continue | Custom Tab opens Persona sandbox over the app (dark toolbar) |
| 3 | Complete sandbox as "approved" | Tab closes on return; "Comprobando…" then "Identidad verificada"; main area "Verificado" |
| 4 | Start again with another user, close the tab early | "No terminaste la verificación" + "Volver a la verificación" reopens Persona |
| 5 | Re-enter "Verificar" with an unfinished verification | Intro shows "Tienes una verificación pendiente" → resumes |
| 6 | Sandbox "declined" | Reason text + "Intentar de nuevo" → empty document step |
| 7 | Sandbox "needs review" / slow webhook | After 60 s: "Estamos revisando tus datos" |
| 8 | Birth date making the user 17 | Error before contacting the backend |
| 9 | Airplane mode on "Continuar a la verificación" | Connection message, data kept, nothing created |
| 10 | `adb shell run-as com.codewave.qullqi ls -R` | No images, no page address stored (SC-004) |
| 11 | Upgrade from a spec 004 build | Opens fine; `kyc_attempt` gone |
| 12 | App closed (force stop) → `adb shell am start -a android.intent.action.VIEW -d qullqi://kyc/result` | Splash → login; KYC never opens without login (security, T016) |
| 13 | Revoke the session in the backend while in Persona, return | "Tu sesión expiró. Ingresa tu PIN." → login for that account (T021a) |
