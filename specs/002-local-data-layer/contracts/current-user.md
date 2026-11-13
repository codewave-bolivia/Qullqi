# Contract: Current User (for M4+)

Status: **Draft** (spec header `**Contract**`). Once approved, M4+ may stack on
`feature/002-local-data-layer` and rely only on what is listed here (CLAUDE.md §15.3).

Package: `com.codewave.qullqi.domain` (shared/src/commonMain).

```kotlin
// domain/model
@JvmInline value class UserId(val value: String)          // stable for the life of the user

enum class VerificationStatus { NOT_VERIFIED, UNDER_REVIEW, VERIFIED }   // exactly three

data class CurrentUser(
    val id: UserId,
    val displayName: String,
    val phone: String,          // "+591XXXXXXXX"
    val email: String,          // lowercase
    val verification: VerificationStatus,
)

// The KYC gate (constitution / ARCHITECTURE.md §5.4): money operations only when VERIFIED.
fun VerificationStatus.canOperateMoney(): Boolean = this == VerificationStatus.VERIFIED

// domain/repository
interface CurrentUserSource {
    /** Emits the logged-in user, or null when there is no session. Emits again on login,
     *  logout, account switch and any verification status change. */
    val currentUser: kotlinx.coroutines.flow.Flow<CurrentUser?>
}
```

## Guarantees

- `currentUser` emits the current value immediately on collection, then every change.
- `verification` reflects the latest final verification attempt (enforced by the store).
- A rejected verification yields `NOT_VERIFIED`; there is no `REJECTED` status.
- `UserId.value` never changes and can be stored by M4+ tables as a foreign key to `user(id)`.
  If M4+ adds tables with `REFERENCES user(id)`, they must declare `ON DELETE CASCADE`.

## Not part of the contract

Credentials, PIN data, sessions table, phone confirmations, terms records, verification attempt
details and image references. These may change without notice.

## Change rule

Any change to this file resets the spec header to `**Contract**: Draft` until re-approved, and the
M4+ owner must be informed before they update their branch.
