package com.codewave.qullqi

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.codewave.qullqi.designsystem.QullqiTheme
import com.codewave.qullqi.feature.main.MainFlow

/**
 * Main area host (spec 003 FR-031). Its content belongs to M4 (`MainFlow`'s content slot); this
 * activity owns the 5-minute re-authentication lock (FR-002b) and the hand-offs to AuthActivity.
 */
class MainActivity : FragmentActivity() {

    private val deps by lazy {
        (application as QullqiApplication).dependencies.copy(biometrics = AndroidBiometricAuthenticator(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep balances/personal data out of the recents thumbnail (API 33+; no equivalent below without FLAG_SECURE).
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        setContent {
            QullqiTheme {
                MainFlow(
                    deps = deps,
                    onLogout = { id -> openAuthClearingTask(AuthActivity.loginIntent(this, id)) },
                    onVerify = {
                        // AuthActivity opens on top at the KYC entry and finishes back into this activity.
                        startActivity(AuthActivity.kycIntent(this))
                        noTransition()
                    },
                    onRequireLogin = { id -> openAuthClearingTask(AuthActivity.loginIntent(this, id)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        deps.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        deps.appLock.onBackground()
    }

    private fun openAuthClearingTask(intent: Intent) {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        noTransition()
        finish()
    }

}
