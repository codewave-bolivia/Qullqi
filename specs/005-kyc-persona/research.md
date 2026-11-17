# Research: Identity Verification with Persona (M3)

## R1. Opening the provider page

- **Decision**: `androidx.browser` `CustomTabsIntent` (toolbar color `#161826`, share and
  bookmarks hidden, `setUrlBarHidingEnabled(true)`), behind
  `interface ExternalPageOpener { fun open(url: String) }` in `commonMain`; `CustomTabPageOpener`
  in `androidApp`. Without a Custom Tabs browser, `CustomTabsIntent.launchUrl` falls back to the
  default browser (FR-005a).
- **Version**: latest stable `androidx.browser` at task T001 (no alpha/beta).
- **Portrait guard**: the tab is the browser's activity, not in the merged manifest; the guard is
  unaffected.
- **Alternatives**: default browser (`LocalUriHandler`) — leaves the app; WebView — camera/file
  permissions by hand, discouraged by the provider.

## R2. Detecting the return

- **Decision**: the Waiting screen starts polling on the **first ON_RESUME after the tab was
  opened** (lifecycle of AuthActivity), whether the user finished (redirect) or closed the tab.
  The redirect `qullqi://kyc/result` targets `KycReturnActivity`, a no-UI trampoline that starts
  `AuthActivity` with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` (closes the tab, reuses
  the existing AuthActivity) and finishes. If no AuthActivity exists (process death), AuthActivity
  starts at `AuthStart.Kyc` and FR-002 routing reads the status.
- **Trampoline theme**: opaque `Theme.Qullqi` + `screenOrientation="portrait"` (translucent +
  fixed orientation crashes on API 26), `excludeFromRecents`, `noHistory`, `exported="true"`, one
  `intent-filter` (`VIEW`, `DEFAULT`, `BROWSABLE`, `qullqi://kyc/result`). It carries no data and
  never trusts parameters.
- **Rationale**: resume covers both "finished" and "closed early"; the link only improves UX.

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
