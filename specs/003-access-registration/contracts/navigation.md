# Contract: Activities, Flow Roots and Navigation

Package `com.codewave.qullqi.navigation` and flow roots in `feature/auth`, `feature/main`
(shared areas). Android hosts in `androidApp`.

## Activities (Android)

| Activity | Role | Launch | Hosts |
|----------|------|--------|-------|
| `AuthActivity` | launcher (exported) | system launch screen → `AuthFlow(start)` | splash, welcome, login, registration, recover PIN, `kycIntro` (M3) |
| `MainActivity` | not exported | only from AuthActivity or the task | `MainFlow` (M4 content) + `UnlockScreen` overlay |

Intent extra `start` for AuthActivity: `Splash` (default), `Login(userId)`, `Kyc`.

| Transition | Mechanism |
|------------|-----------|
| Login / biometric / "Hacerlo más tarde" → main | veil out → `MainActivity`, `NEW_TASK | CLEAR_TASK` |
| "Verificar mi identidad" (regDone) | push `KycIntro` inside AuthActivity; `onKycDone` → MainActivity (`CLEAR_TASK`) |
| "Verificar" (MainActivity) | veil out → `AuthActivity(start = Kyc)` on top; KYC end → `finish()` → MainActivity revealed |
| Logout | veil out → `AuthActivity(start = Login(id))`, `NEW_TASK | CLEAR_TASK` |
| Back at a flow root | not consumed → activity finishes → app exits (FR-033) |

## Shared flow roots

```kotlin
sealed interface AuthStart { data object Splash : AuthStart; data class Login(val userId: UserId) : AuthStart; data object Kyc : AuthStart }

@Composable fun AuthFlow(deps: AppDependencies, start: AuthStart,
                         onEnterMain: () -> Unit, onKycDone: () -> Unit,
                         kycEntry: @Composable (onDone: () -> Unit) -> Unit = { KycPlaceholderScreen(it) })

@Composable fun MainFlow(deps: AppDependencies, onLogout: (UserId) -> Unit, onVerify: () -> Unit,
                         content: @Composable () -> Unit = { MainPlaceholderScreen(onLogout, onVerify) })

@Composable fun QullqiRoot(deps: AppDependencies)   // iOS/Desktop: one window, swaps AuthFlow/MainFlow
```

In-activity routes:

```kotlin
sealed interface AuthRoute { Splash, Welcome, Login(userId?), RegPhone, RegCode, RegInfo, RegPin, RegDone, RecoverPin(userId), KycIntro }
class FlowNavigator<R>(start: R) { val stack: StateFlow<List<R>>; fun push(r: R); fun replace(r: R); fun resetTo(r: R); fun back(): Boolean }
```

## Hand-off rules for M3 / M4

- **M4** replaces `MainFlow`'s `content` slot (default: placeholder). It must not edit
  `MainActivity`'s lock handling; it calls `onLogout(id)` / `onVerify()`.
- **M3** replaces `AuthFlow`'s `kycEntry` slot; it calls `onDone()` when verification finishes.
- Changing these signatures, activity extras or flags is a shared-area change (coordinate).
