package com.codewave.qullqi.feature.main

import androidx.compose.runtime.Composable
import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.feature.auth.PendingRoute

// Temporary bodies, replaced in US6 (T064, T065).
@Composable
internal fun MainPlaceholderScreen(deps: AppDependencies, actions: MainActions) = PendingRoute("MainPlaceholder")

@Composable
internal fun UnlockScreen(deps: AppDependencies, actions: MainActions) = PendingRoute("Unlock")
