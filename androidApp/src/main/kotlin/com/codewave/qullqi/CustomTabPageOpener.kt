package com.codewave.qullqi

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.codewave.qullqi.domain.system.ExternalPageOpener

/**
 * Opens the identity provider's page in a Custom Tab over the app (spec 005 FR-005a). Without a
 * Custom Tabs browser, `launchUrl` falls back to the default browser. The tab is the browser's
 * activity, so the portrait guard is unaffected.
 */
class CustomTabPageOpener(private val activity: Activity) : ExternalPageOpener {
    override fun open(url: String) {
        val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(NOCTURNE_BG).build()
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colors)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setUrlBarHidingEnabled(true)
            .setShowTitle(true)
            .build()
            .launchUrl(activity, Uri.parse(url))
    }

    private companion object {
        /** Nocturne `bg` (#161826), as in Theme.Qullqi's window background. */
        const val NOCTURNE_BG = 0xFF161826.toInt()
    }
}
