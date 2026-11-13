package com.codewave.qullqi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.codewave.qullqi.feature.auth.AuthFlow
import com.codewave.qullqi.feature.auth.AuthStart
import com.codewave.qullqi.feature.main.MainFlow

/**
 * Single-window host for platforms without activities (iOS/Desktop, FR-034): swaps the two
 * independent roots exactly like AuthActivity ⇄ MainActivity on Android.
 */
@Composable
fun QullqiRoot(deps: AppDependencies) {
    var screen by remember { mutableStateOf<Root>(Root.Auth(AuthStart.Splash)) }
    when (val s = screen) {
        is Root.Auth -> AuthFlow(
            deps = deps,
            start = s.start,
            onEnterMain = { screen = Root.Main },
            onKycDone = { screen = Root.Main },
        )
        Root.Main -> MainFlow(
            deps = deps,
            onLogout = { screen = Root.Auth(AuthStart.Login(it)) },
            onVerify = { screen = Root.Auth(AuthStart.Kyc) },
            onRequireLogin = { screen = Root.Auth(AuthStart.Login(it)) },
        )
    }
}

private sealed interface Root {
    data class Auth(val start: AuthStart) : Root
    data object Main : Root
}
