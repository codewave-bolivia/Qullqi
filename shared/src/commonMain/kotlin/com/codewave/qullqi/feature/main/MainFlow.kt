package com.codewave.qullqi.feature.main

import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.codewave.qullqi.feature.auth.AuthMessages
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
    // "Verificar" covers this activity with the veil before AuthActivity opens on top; when that
    // finishes back here nothing else would lift it (empty dark screen). Reveal on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { veil.reveal() } }
    val actions = remember {
        MainActions(
            toast = toast,
            logout = { id -> scope.launch { veil.coverThen { onLogout(id) } } },
            verify = { scope.launch { veil.coverThen(onVerify) } },
            requireLogin = { id -> scope.launch { veil.coverThen { onRequireLogin(id) } } },
        )
    }
    // Session ended by a rejected renewal (not by logout): back to login once (spec 004, FR-016a).
    // Only while visible: when AuthActivity runs on top (identity verification), AuthFlow handles it.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            deps.session.sessionExpired.collect { id ->
                toast.show(AuthMessages.SESSION_EXPIRED)
                actions.requireLogin(id)
            }
        }
    }
    val locked by deps.appLock.locked.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (locked) UnlockScreen(deps, actions) else content(actions)
        QToastHost(toast)
        VeilOverlay(veil)
    }
}
