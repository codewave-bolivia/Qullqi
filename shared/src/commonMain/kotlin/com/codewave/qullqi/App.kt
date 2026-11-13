package com.codewave.qullqi

import androidx.compose.runtime.Composable
import com.codewave.qullqi.designsystem.QullqiTheme
import com.codewave.qullqi.feature.main.UnavailableScreen

/**
 * Entry for single-window platforms. Desktop and iOS call `App()` unchanged and get
 * [UnavailableScreen] until the Builder provides their database driver and PIN hasher; passing
 * [dependencies] runs the full app through [QullqiRoot]. Android uses AuthActivity/MainActivity instead.
 */
@Composable
fun App(dependencies: AppDependencies? = null) {
    QullqiTheme {
        if (dependencies == null) UnavailableScreen() else QullqiRoot(dependencies)
    }
}
