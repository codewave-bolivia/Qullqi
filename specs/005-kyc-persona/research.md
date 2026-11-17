# Research: Identity Verification with Persona (M3)

## R1. Opening the provider page — in-app WebView (rev. 2026-11-17, supersedes Custom Tab)

- **Decision**: `KycWebViewActivity` (androidApp, `exported="false"`, portrait, `Theme.Qullqi`)
  hosting an Android `WebView` inside a Compose screen (Nocturne top bar with a close button).
  Opened through `ProviderPage` (`commonMain` interface, see contracts/kyc-repository.md):
  `suspend fun open(url): PageOpen` — `AuthActivity` first requests `CAMERA` at runtime
  (`ActivityResultContracts.RequestPermission`, registered in `onCreate`, awaited with a
  `CompletableDeferred`), then starts the activity with the URL as an extra.
- **WebView safeguards** (constitution v2.2.0 Principle I):
  - `javaScriptEnabled = true`, `domStorageEnabled = true` (the provider needs them);
    `mediaPlaybackRequiresUserGesture = false`; `allowFileAccess = false`,
    `allowContentAccess = false`; **no `addJavascriptInterface`**; `setWebContentsDebuggingEnabled`
    never enabled.
  - `WebViewClient.shouldOverrideUrlLoading`: `qullqi://kyc/result` → `finish()`; `https` and host =
    the start URL's host or `*.withpersona.com` → load; anything else → `Intent.ACTION_VIEW` in the
    system browser, not loaded in the app. `onReceivedSslError` → cancel.
  - `WebChromeClient.onPermissionRequest`: grant only `RESOURCE_VIDEO_CAPTURE`, only for an allowed
    origin, only when the app holds `CAMERA`; deny everything else. `onShowFileChooser` → `false`
    (camera only, FR-005c).
  - On destroy: `CookieManager.removeAllCookies`, `WebStorage.deleteAllData`, `clearCache(true)`,
    `destroy()`. The URL is never logged; the extra is dropped on recreation: if
    `savedInstanceState != null` the activity finishes (no page restored after process death).
- **Camera permission** (FR-005b): denied → `PageOpen.CameraDenied(permanent)` (permanent when
  `shouldShowRequestPermissionRationale` is false after a denial); the page is not opened.
  `ProviderPage.openCameraSettings()` opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
  Manifest: `<uses-permission android:name="android.permission.CAMERA"/>` and
  `<uses-feature android:name="android.hardware.camera" android:required="false"/>`.
- **Removed**: `androidx.browser`, `CustomTabPageOpener`, `KycReturnActivity` and its exported
  deep-link filter (no public entry point any more).
- **Alternatives**: Custom Tab (previous decision; owner switched to WebView), system browser.

## R2. Detecting the return

- The WebView intercepts `qullqi://kyc/result` and finishes; the close button and back (when the page
  cannot go back) also finish it. In every case `AuthActivity` resumes → the KYC flow polls (same
  resume-based detection as before, R3). No deep link reaches the app from outside.

## R3. Polling (FR-007)

- After return: `GET /my/kyc` immediately, then every 5 s, max 60 s (13 calls). `CREATED` → stop,
  "No terminaste la verificación" + "Volver a la verificación"; `PENDING` → keep polling, at the
  end show in-review; `APPROVED`/`DECLINED` → result. `Unavailable` → keep trying until the limit,
  then show the connection message with "Reintentar".
- Polling runs in `viewModelScope` with `delay` (virtual time in tests); stops when the screen
  leaves.

## R4. Contract replacement

- The spec 002 KYC model (`KycAttempt`, drafts, placeholder images, `MockKycProcessor`, the
  `kyc_attempt` table and its trigger that writes `user.verification`) is removed: capture lives in
  Persona and the status in the backend. Migration `2.sqm`:
  `DROP TRIGGER kyc_attempt_final_sets_user_status; DROP INDEX kyc_attempt_one_open; DROP INDEX
  kyc_attempt_history; DROP TABLE kyc_attempt;`. Snapshot `2.db` generated before editing (same
  procedure as spec 004 R5).
- `CurrentUserContractTest` keeps its contract checks using `user.verification` updates instead of
  the KYC trigger.

## R5. Birth date without a date library

- **Decision**: input as 8 digits shown `DD/MM/AAAA` (numeric keyboard, auto slashes); validation
  in `domain`: real calendar date (month lengths, leap years), in the past, age ≥ 18 against an
  injected `Today` (`() -> SimpleDate`, `androidApp` uses `java.time.LocalDate.now()`; tests fixed).
  Sent as ISO `YYYY-MM-DD`.
- **Rationale**: no `kotlinx-datetime` dependency for one field; the backend validates again.

## R6. Backend gaps handled in the app

- `POST /my/kyc` answers `PENDING` with a `hostedUrl` (change request C1): the app opens whatever
  `hostedUrl` comes back and routes by a fresh `GET` after return.
- Expired page address (H1): reopening always uses the latest `GET /my/kyc` `hostedUrl`.
- Attempt limit undefined (H2): `403 kyc_rejected` → "Contacta a soporte".
- No CI complement (C2): not asked.

## R7. Decline reasons (FR-010)

`data_mismatch` → "Los datos no coinciden con tu documento."; `document_unreadable`,
`document_expired` → "No pudimos leer tu documento o está vencido."; `identity_not_verified`,
`selfie_mismatch` → "No pudimos confirmar que eres tú."; other/null → "No pudimos verificar tu
identidad."
