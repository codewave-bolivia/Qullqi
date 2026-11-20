package com.codewave.qullqi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.codewave.qullqi.designsystem.QullqiTheme
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.feature.auth.AuthFlow
import com.codewave.qullqi.feature.auth.AuthStart

/**
 * Launcher activity (spec 003 FR-031): system launch screen → splash, welcome, login,
 * registration, PIN recovery and the M3 verification entry.
 */
class AuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded system launch screen; dismissed on the first frame, which is the in-app splash
        // on the same #161826 background (FR-001a).
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Registered here: activity-result launchers must exist before STARTED (spec 005).
        val providerPage = AndroidProviderPage(this)
        val start = parseStart(intent.getStringExtra(EXTRA_START))
        val deps = (application as QullqiApplication).dependencies.copy(
            biometrics = AndroidBiometricAuthenticator(this),
            providerPage = providerPage,
        )
        setContent {
            QullqiTheme {
                AuthFlow(
                    deps = deps,
                    start = start,
                    onEnterMain = { openMain() },
                    onKycDone = { if (start == AuthStart.Kyc) finishWithoutAnimation() else openMain() },
                )
            }
        }
    }

    /** Clears the task so back from MainActivity never returns to auth screens (FR-033). */
    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        noTransition()
        finish()
    }

    private fun finishWithoutAnimation() {
        finish()
        noTransition(closing = true)
    }

    companion object {
        private const val EXTRA_START = "start"

        fun loginIntent(context: Context, userId: UserId) =
            Intent(context, AuthActivity::class.java).putExtra(EXTRA_START, "login:${userId.value}")

        fun kycIntent(context: Context) = Intent(context, AuthActivity::class.java).putExtra(EXTRA_START, "kyc")

        private fun parseStart(raw: String?): AuthStart = when {
            raw == "kyc" -> AuthStart.Kyc
            raw != null && raw.startsWith("login:") -> AuthStart.Login(UserId(raw.removePrefix("login:")))
            else -> AuthStart.Splash
        }
    }
}

/** The veil already animated in Compose; the activity switch itself must not add a system animation. */
internal fun Activity.noTransition(closing: Boolean = false) = activityTransition(closing, 0, 0)

/** Cross-fade between activities (spec 005 T051: provider page). */
internal fun Activity.fadeTransition(closing: Boolean = false) =
    activityTransition(closing, android.R.anim.fade_in, android.R.anim.fade_out)

@Suppress("DEPRECATION")
private fun Activity.activityTransition(closing: Boolean, enter: Int, exit: Int) {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(if (closing) Activity.OVERRIDE_TRANSITION_CLOSE else Activity.OVERRIDE_TRANSITION_OPEN, enter, exit)
    } else {
        overridePendingTransition(enter, exit)
    }
}
