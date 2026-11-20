# Quickstart: Identity Verification with Persona (M3)

## Prerequisites

- Spec 004 integrated; local dev backend with Persona **sandbox** credentials configured
  (backend side) and the redirect `qullqi://kyc/result`.
- Debug build on the device (`qullqi.api.url.debug` pointing to the PC).

## Automated checks

```bash
./gradlew :shared:verifyCommonMainQullqiDatabaseMigration   # v2 → v3 (drop kyc_attempt)
./gradlew :shared:testAndroidHostTest                       # RemoteKycRepository, KycViewModel (polling), domain dates
./gradlew :androidApp:assembleDebug                         # portrait guard includes KycWebViewActivity
```

## Manual scenarios (device)

| # | Steps | Expected |
|---|---|---|
| 1 | Not verified user → "Verificar" → intro | Requirements + privacy; "Continuar" disabled until accepted |
| 2 | Accept privacy → "Continuar" (no document form) | Camera permission prompt → Persona sandbox opens inside the app (Nocturne bar, close button) |
| 3 | Complete sandbox as "approved" | WebView closes on return; "Comprobando…" then "Identidad verificada"; main area "Verificado" |
| 4 | Start again with another user, close the WebView early (X) | "No terminaste la verificación" + "Volver a la verificación" reopens Persona |
| 5 | Re-enter "Verificar" with an unfinished verification | Intro shows "Tienes una verificación pendiente" → resumes |
| 6 | Sandbox "declined" | Reason text + "Intentar de nuevo" → intro (new verification) |
| 7 | Sandbox "needs review" / slow webhook | After 60 s: "Estamos revisando tus datos" |
| 8 | Sandbox document of a minor | Declined: "Debes ser mayor de 18 años…" |
| 9 | Airplane mode on "Continuar" | Connection message on the intro, nothing created |
| 10 | `adb shell run-as com.codewave.qullqi ls -R` | No images, no page address stored (SC-004) |
| 11 | Upgrade from a spec 004 build | Opens fine; `kyc_attempt` gone |
| 12 | `adb shell am start -a android.intent.action.VIEW -d qullqi://kyc/result` | No app activity handles it (no exported deep link) |
| 14 | Deny camera (then "don't ask again") | Page does not open; "Necesitamos tu cámara…" → "Permitir cámara" asks again / opens app settings |
| 15 | Tap a non-provider link inside Persona (e.g. privacy policy) | Opens in the system browser, not in the app |
| 13 | Revoke the session in the backend while in Persona, return | "Tu sesión expiró. Ingresa tu PIN." → login for that account (T021a) |
| 16 | Tap "Continuar"; then close Persona | Fade to Nocturne loader, Persona fades in; fade back on close; no white frame (screen recording) |
