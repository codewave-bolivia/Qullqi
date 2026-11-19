package com.codewave.qullqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.codewave.qullqi.feature.kyc.ProviderWebScreen

/**
 * The identity provider's page inside the app (spec 005 FR-005a/c, FR-006; constitution v2.2.0):
 * only the provider's HTTPS origin, camera only (no file chooser), no bridge to app code, return
 * link intercepted here, cookies/storage/cache cleared on close, the address is never logged.
 */
class KycWebViewActivity : ComponentActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        // Never restore the page after process death or recreation: the flow re-reads the status.
        if (savedInstanceState != null || url == null || !isAllowed(Uri.parse(url), startHost = null)) {
            finish()
            return
        }
        val startHost = Uri.parse(url).host
        // Persona's WebView flow must be activated explicitly (docs: quickstart-android-webview):
        // without `is-webview=true` the hosted page assumes a full browser and may render blank.
        val pageUrl = Uri.parse(url).let { uri ->
            if (uri.getQueryParameter("is-webview") != null) url
            else uri.buildUpon().appendQueryParameter("is-webview", "true").build().toString()
        }
        setContent {
            BackHandler {
                val view = webView
                if (view != null && view.canGoBack()) view.goBack() else finish()
            }
            ProviderWebScreen { context ->
                buildWebView(context, startHost).also { webView = it; it.loadUrl(pageUrl) }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled") // the provider's page needs JavaScript; no JS bridge is added
    private fun buildWebView(context: Context, startHost: String?) = WebView(context).apply {
        // Compose's AndroidView gives the view WRAP_CONTENT params; a WebView with wrap_content height
        // lays the page out with a 0 px viewport, so the provider's 100%/100vh content is clipped
        // (only fixed elements show). MATCH_PARENT gives the page the real height.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Debug builds only: inspect the provider page from chrome://inspect (never in release).
        WebView.setWebContentsDebuggingEnabled(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return when {
                    uri.scheme == RETURN_SCHEME && uri.host == RETURN_HOST -> { finish(); true }
                    isAllowed(uri, startHost) -> false
                    else -> { openOutside(uri); true }
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val camera = PermissionRequest.RESOURCE_VIDEO_CAPTURE
                val allowed = isAllowed(request.origin, startHost) &&
                    request.resources.all { it == camera } &&
                    ContextCompat.checkSelfPermission(this@KycWebViewActivity, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (allowed) request.grant(arrayOf(camera)) else request.deny()
            }

            // Camera only: no file chooser (FR-005c).
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean = false
        }
    }

    private fun openOutside(uri: Uri) {
        if (uri.scheme == "https" || uri.scheme == "http") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    override fun onDestroy() {
        webView?.let {
            it.stopLoading()
            it.clearCache(true)
            it.destroy()
        }
        webView = null
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        super.onDestroy()
        noTransition(closing = true)
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val RETURN_SCHEME = "qullqi"
        private const val RETURN_HOST = "kyc"
        private const val PROVIDER_DOMAIN = "withpersona.com"

        fun intent(context: Context, url: String): Intent =
            Intent(context, KycWebViewActivity::class.java).putExtra(EXTRA_URL, url)

        /** HTTPS and the start host or the provider's domain (constitution v2.2.0). */
        internal fun isAllowed(uri: Uri, startHost: String?): Boolean {
            val host = uri.host ?: return false
            return uri.scheme == "https" &&
                (host == startHost || host == PROVIDER_DOMAIN || host.endsWith(".$PROVIDER_DOMAIN"))
        }
    }
}
