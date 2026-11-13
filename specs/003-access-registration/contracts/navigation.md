# Contract: Navigation and Hand-off

Package `com.codewave.qullqi.navigation` (shared area).

```kotlin
sealed interface Route {
    // M2 (this feature)
    data object Splash : Route
    data object Welcome : Route
    data class Login(val userId: UserId? = null) : Route   // null → most recent account
    data object RegPhone : Route
    data object RegCode : Route
    data object RegInfo : Route
    data object RegPin : Route
    data object RegDone : Route
    data class RecoverPin(val userId: UserId) : Route
    // Hand-off entry points (owners replace the placeholder screens)
    data object MainArea : Route          // owner: M4 (other developer)
    data object KycEntry : Route          // owner: M3
}

class Navigator(start: Route) {
    val stack: StateFlow<List<Route>>
    fun push(route: Route)
    fun replace(route: Route)             // replaces the top (e.g. Checking → result)
    fun resetTo(route: Route)             // clears the stack (login → MainArea, logout → Login)
    fun back(): Boolean                   // false at root
}
```

## Rules

- Entering the app (login, biometric, registration "más tarde") → `resetTo(MainArea)`; back cannot
  return to auth screens.
- Logout → `resetTo(Login(userId))`.
- Registration steps use `push`; back keeps the ViewModel draft.
- Transitions: push = enter from +26 dp + fade, back = from −26 dp + fade, 300 ms
  `cubic-bezier(.2,.8,.2,1)`; `resetTo` = fade 250 ms.

## Hand-off for M3 and M4

- `MainArea` and `KycEntry` are rendered by `@Composable` slots passed to `App`:
  `App(dependencies, mainArea = { MainPlaceholderScreen() }, kycEntry = { KycPlaceholderScreen() })`.
  M3/M4 replace the default slot content; they do not edit M2 screens.
- Inside their slots, M3/M4 may use `Navigator.resetTo(Route.Login(...))` for logout and
  `Navigator.push(Route.KycEntry)` for "Verificar".
- Changing `Route` names or `Navigator` signatures requires coordination (shared area).
