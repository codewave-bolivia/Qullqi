package com.codewave.qullqi.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.codewave.qullqi.feature.auth.login.AccountsSheet
import com.codewave.qullqi.feature.auth.login.EnableBiometricsSheet
import com.codewave.qullqi.feature.auth.login.LoginEffect
import com.codewave.qullqi.feature.auth.login.LoginMode
import com.codewave.qullqi.feature.auth.login.LoginScreen
import com.codewave.qullqi.feature.auth.login.LoginViewModel
import com.codewave.qullqi.feature.auth.register.CodeStep
import com.codewave.qullqi.feature.auth.register.DoneScreen
import com.codewave.qullqi.feature.auth.register.InfoStep
import com.codewave.qullqi.feature.auth.register.PhoneStep
import com.codewave.qullqi.feature.auth.register.PinStep
import com.codewave.qullqi.feature.auth.register.RegEffect
import com.codewave.qullqi.feature.auth.register.RegistrationViewModel
import com.codewave.qullqi.feature.auth.recover.RecoverEffect
import com.codewave.qullqi.feature.auth.recover.RecoverPinScreen
import com.codewave.qullqi.feature.auth.recover.RecoverPinViewModel
import com.codewave.qullqi.feature.auth.start.SplashScreen
import com.codewave.qullqi.feature.auth.start.StartEffect
import com.codewave.qullqi.feature.auth.start.StartViewModel
import com.codewave.qullqi.feature.auth.start.WelcomeScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.codewave.qullqi.AppDependencies
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
    /** Debug reset: route again from the splash with a fresh StartViewModel. */
    val restart: () -> Unit,
    /** Changes after each [restart] so start-up ViewModels are recreated. */
    val generation: () -> Int,
    /** Starts a new registration with a fresh draft (FR-014: the draft lives only inside the flow). */
    val startRegistration: () -> Unit,
    val registrationId: () -> Int,
    /** Starts PIN recovery for an account with a fresh ViewModel. */
    val startRecover: (UserId) -> Unit,
    val recoverId: () -> Int,
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
    var generation by remember { mutableIntStateOf(0) }
    var registrationId by remember { mutableIntStateOf(0) }
    var recoverId by remember { mutableIntStateOf(0) }
    val actions = remember {
        AuthActions(
            navigator = navigator,
            toast = toast,
            enterMain = { scope.launch { veil.coverThen(onEnterMain) } },
            kycDone = { scope.launch { veil.coverThen(onKycDone) } },
            restart = { generation++; navigator.resetTo(AuthRoute.Splash) },
            generation = { generation },
            startRegistration = { registrationId++; navigator.push(AuthRoute.RegPhone) },
            registrationId = { registrationId },
            startRecover = { id -> recoverId++; navigator.push(AuthRoute.RecoverPin(id)) },
            recoverId = { recoverId },
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
        AuthRoute.Splash, AuthRoute.Welcome -> StartRoute(route, deps, actions)
        is AuthRoute.Login -> LoginRoute(route, deps, actions)
        AuthRoute.RegPhone, AuthRoute.RegCode, AuthRoute.RegInfo, AuthRoute.RegPin, AuthRoute.RegDone ->
            RegistrationRoute(route, deps, actions)
        is AuthRoute.RecoverPin -> RecoverRoute(route, deps, actions)
        AuthRoute.KycIntro -> kycEntry(actions.kycDone)
    }
}

@Composable
private fun StartRoute(route: AuthRoute, deps: AppDependencies, actions: AuthActions) {
    val vm = viewModel(key = "start-${actions.generation()}") { StartViewModel(deps.preferences) }
    LaunchedEffect(vm) {
        vm.effects.collect { e ->
            when (e) {
                // From the splash the target replaces it, so back never returns to the splash.
                is StartEffect.ToLogin ->
                    if (route == AuthRoute.Splash) actions.navigator.replace(AuthRoute.Login(e.userId))
                    else actions.navigator.push(AuthRoute.Login(e.userId))
                StartEffect.ToWelcome -> actions.navigator.replace(AuthRoute.Welcome)
                StartEffect.ToRegistration -> actions.startRegistration()
                is StartEffect.Toast -> actions.toast.show(e.text)
            }
        }
    }
    if (route == AuthRoute.Splash) SplashScreen(deps.debugReset, actions.restart) else WelcomeScreen(vm::onEvent)
}

@Composable
private fun LoginRoute(route: AuthRoute.Login, deps: AppDependencies, actions: AuthActions) {
    val vm = viewModel(key = "login-${route.userId?.value}-${actions.generation()}") {
        LoginViewModel(route.userId, LoginMode.Login, deps.session, deps.preferences, deps.biometrics, deps.appLock)
    }
    LaunchedEffect(vm) {
        vm.effects.collect { e ->
            when (e) {
                LoginEffect.EnterMain -> actions.enterMain()
                LoginEffect.ToRegistration -> actions.startRegistration()
                is LoginEffect.ToRecover -> actions.startRecover(e.userId)
                LoginEffect.Unlocked -> Unit
            }
        }
    }
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        LoginScreen(state, vm::onEvent)
        AccountsSheet(state, vm::onEvent)
        EnableBiometricsSheet(state, vm::onEvent)
    }
}

@Composable
private fun RegistrationRoute(route: AuthRoute, deps: AppDependencies, actions: AuthActions) {
    // One ViewModel shared by the 4 steps of this registration.
    val vm = viewModel(key = "registration-${actions.registrationId()}") {
        RegistrationViewModel(deps.registration, deps.session, deps.preferences, deps.appLock)
    }
    LaunchedEffect(vm, route) {
        vm.effects.collect { e ->
            when (e) {
                RegEffect.ToCode -> if (route != AuthRoute.RegCode) actions.navigator.push(AuthRoute.RegCode)
                RegEffect.ToInfo -> actions.navigator.push(AuthRoute.RegInfo)
                RegEffect.ToPin -> actions.navigator.push(AuthRoute.RegPin)
                // Account created: the steps can no longer be revisited.
                RegEffect.ToDone -> actions.navigator.resetTo(AuthRoute.RegDone)
                is RegEffect.ToLogin -> actions.navigator.resetTo(AuthRoute.Login(e.userId))
                is RegEffect.Toast -> actions.toast.show(e.text)
            }
        }
    }
    val state by vm.state.collectAsState()
    val back = { actions.navigator.back(); Unit }
    when (route) {
        AuthRoute.RegPhone -> PhoneStep(state, vm::onEvent, back)
        AuthRoute.RegCode -> CodeStep(state, vm::onEvent, back)
        AuthRoute.RegInfo -> InfoStep(state, vm::onEvent, back)
        AuthRoute.RegPin -> PinStep(state, vm::onEvent, back)
        else -> DoneScreen(
            firstName = state.createdName,
            onVerify = { actions.navigator.push(AuthRoute.KycIntro) },
            onLater = actions.enterMain,
        )
    }
}

@Composable
private fun RecoverRoute(route: AuthRoute.RecoverPin, deps: AppDependencies, actions: AuthActions) {
    val vm = viewModel(key = "recover-${route.userId.value}-${actions.recoverId()}") {
        RecoverPinViewModel(route.userId, deps.registration, deps.session)
    }
    LaunchedEffect(vm) {
        vm.effects.collect { e ->
            when (e) {
                is RecoverEffect.Toast -> actions.toast.show(e.text)
                // Fresh login for the account, with attempts reset (FR-015).
                is RecoverEffect.ToLogin -> actions.navigator.resetTo(AuthRoute.Login(e.userId))
            }
        }
    }
    val state by vm.state.collectAsState()
    RecoverPinScreen(state, vm::onEvent) { actions.navigator.back() }
}
