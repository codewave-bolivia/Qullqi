package com.codewave.qullqi.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.VeilOverlay
import com.codewave.qullqi.designsystem.components.QToastHost
import com.codewave.qullqi.designsystem.components.QToastState
import com.codewave.qullqi.designsystem.rememberVeilState
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.navigation.FlowHost
import com.codewave.qullqi.navigation.FlowNavigator
import kotlinx.coroutines.launch

/** Where AuthActivity starts (intent extra, contracts/navigation.md). */
sealed interface AuthStart {
    data object Splash : AuthStart
    data class Login(val userId: UserId) : AuthStart
    data object Kyc : AuthStart
}

/** Screens hosted by AuthActivity. */
sealed interface AuthRoute {
    data object Splash : AuthRoute
    data object Welcome : AuthRoute
    data class Login(val userId: UserId?) : AuthRoute
    data object RegPhone : AuthRoute
    data object RegCode : AuthRoute
    data object RegInfo : AuthRoute
    data object RegPin : AuthRoute
    data object RegDone : AuthRoute
    data class RecoverPin(val userId: UserId) : AuthRoute
    data object KycIntro : AuthRoute
}

/** Callbacks a route body uses to move within AuthActivity or leave it. */
class AuthActions(
    val navigator: FlowNavigator<AuthRoute>,
    val toast: QToastState,
    /** Covers with the veil, then leaves for MainActivity. */
    val enterMain: () -> Unit,
    /** M3 entry finished: back to MainActivity (or into it after registration). */
    val kycDone: () -> Unit,
)

/**
 * Root of AuthActivity (FR-031): splash, welcome, login, registration, PIN recovery and the M3 entry.
 * [kycEntry] is the slot M3 replaces; it calls `onDone` when verification ends.
 */
@Composable
fun AuthFlow(
    deps: AppDependencies,
    start: AuthStart,
    onEnterMain: () -> Unit,
    onKycDone: () -> Unit,
    kycEntry: @Composable (onDone: () -> Unit) -> Unit = { KycPlaceholderScreen(it) },
) {
    val startRoute = when (start) {
        AuthStart.Splash -> AuthRoute.Splash
        is AuthStart.Login -> AuthRoute.Login(start.userId)
        AuthStart.Kyc -> AuthRoute.KycIntro
    }
    val navigator = remember { FlowNavigator(startRoute) }
    val toast = remember { QToastState() }
    // Arriving from MainActivity (logout, verify) the veil uncovers the screen; a cold start uses the launch screen.
    val veil = rememberVeilState(startCovered = start != AuthStart.Splash)
    val scope = rememberCoroutineScope()
    val actions = remember {
        AuthActions(
            navigator = navigator,
            toast = toast,
            enterMain = { scope.launch { veil.coverThen(onEnterMain) } },
            kycDone = { scope.launch { veil.coverThen(onKycDone) } },
        )
    }
    Box(Modifier.fillMaxSize()) {
        FlowHost(navigator) { route -> AuthRouteContent(route, deps, actions, kycEntry) }
        QToastHost(toast)
        VeilOverlay(veil)
    }
}

@Composable
private fun AuthRouteContent(
    route: AuthRoute,
    deps: AppDependencies,
    actions: AuthActions,
    kycEntry: @Composable (onDone: () -> Unit) -> Unit,
) {
    when (route) {
        AuthRoute.KycIntro -> kycEntry(actions.kycDone)
        else -> PendingRoute(route.toString())
    }
}

/** Temporary body for routes implemented in later tasks. */
@Composable
internal fun PendingRoute(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name, color = Q.colors.tx2) }
}
