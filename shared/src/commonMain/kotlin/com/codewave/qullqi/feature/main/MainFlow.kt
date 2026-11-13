package com.codewave.qullqi.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.designsystem.VeilOverlay
import com.codewave.qullqi.designsystem.components.QToastHost
import com.codewave.qullqi.designsystem.components.QToastState
import com.codewave.qullqi.designsystem.rememberVeilState
import com.codewave.qullqi.domain.model.UserId
import kotlinx.coroutines.launch

/** Callbacks for the main area; every one of them leaves MainActivity behind the veil. */
class MainActions(
    val toast: QToastState,
    val logout: (UserId) -> Unit,
    val verify: () -> Unit,
    /** Unlock screen → "Recuperar PIN": go to AuthActivity login for that account. */
    val requireLogin: (UserId) -> Unit,
)

/**
 * Root of MainActivity (FR-031). [content] is the M4 slot (placeholder until the other developer
 * replaces it). While [com.codewave.qullqi.domain.session.AppLock] is locked, [content] is not composed
 * at all; [UnlockScreen] is shown instead (FR-002b).
 */
@Composable
fun MainFlow(
    deps: AppDependencies,
    onLogout: (UserId) -> Unit,
    onVerify: () -> Unit,
    onRequireLogin: (UserId) -> Unit,
    content: @Composable (MainActions) -> Unit = { MainPlaceholderScreen(deps, it) },
) {
    val toast = remember { QToastState() }
    val veil = rememberVeilState(startCovered = true)
    val scope = rememberCoroutineScope()
    val actions = remember {
        MainActions(
            toast = toast,
            logout = { id -> scope.launch { veil.coverThen { onLogout(id) } } },
            verify = { scope.launch { veil.coverThen(onVerify) } },
            requireLogin = { id -> scope.launch { veil.coverThen { onRequireLogin(id) } } },
        )
    }
    val locked by deps.appLock.locked.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (locked) UnlockScreen(deps, actions) else content(actions)
        QToastHost(toast)
        VeilOverlay(veil)
    }
}
