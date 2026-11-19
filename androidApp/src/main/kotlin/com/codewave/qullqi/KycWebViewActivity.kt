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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.codewave.qullqi.feature.kyc.ProviderWebScreen

/**
 * The identity provider's page inside the app (spec 005 FR-005a/c, FR-006; constitution v2.2.0):
 * only the provider's HTTPS origin, camera only (no file chooser), no bridge to app code, return
 * link intercepted here, cookies/storage/cache cleared on close, the address is never logged.
 */
class KycWebViewActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var onFirstPage: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // T051: cross-fade in and out (API 34+ here; older APIs in AndroidProviderPage and finish()).
        fadeTransition()
        fadeTransition(closing = true)
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
        // T050: loader until Persona's first page has painted; fallback so it never hangs.
        var loading by mutableStateOf(true)
        // The WebView stays visible (so it keeps drawing) under an opaque loader; hiding it with
        // alpha 0 stops its drawing and the stale white frame shows when it is revealed.
        val reveal: () -> Unit = { loading = false }
        onFirstPage = reveal
        window.decorView.postDelayed({ if (onFirstPage != null) { onFirstPage = null; reveal() } }, LOADER_TIMEOUT_MS)
        setContent {
            BackHandler {
                val view = webView
                if (view != null && view.canGoBack()) view.goBack() else finish()
            }
            ProviderWebScreen(loading) { context ->
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
        // T048: Nocturne instead of the WebView's default white before the provider paints.
        setBackgroundColor(ContextCompat.getColor(context, R.color.qullqi_bg))
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
                    // Return link = the user finished in the provider (the webhook may still be on its way).
                    uri.scheme == RETURN_SCHEME && uri.host == RETURN_HOST -> { setResult(RESULT_OK); finish(); true }
                    isAllowed(uri, startHost) -> false
                    else -> { openOutside(uri); true }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (onFirstPage != null && isAllowed(Uri.parse(url), startHost)) whenPainted(view)
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

    /**
     * Persona renders with JavaScript after the document loads, first a white loading screen: reveal
     * the page only once its theme is applied (see [PAINTED_JS]) and that state is on screen. Read-only check, no
     * bridge to app code; the loader timeout still applies.
     */
    private fun whenPainted(view: WebView) {
        val reveal = onFirstPage ?: return
        if (isDestroyed) return
        view.evaluateJavascript(PAINTED_JS) { painted ->
            if (onFirstPage == null) return@evaluateJavascript
            if (painted == "true") {
                // Fires once this DOM state is actually drawn on screen, not just computed.
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (onFirstPage != null) { onFirstPage = null; reveal() }
                    }
                })
            } else {
                view.postDelayed({ whenPainted(view) }, PAINT_POLL_MS)
            }
        }
    }

    private fun openOutside(uri: Uri) {
        if (uri.scheme == "https" || uri.scheme == "http") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    override fun onDestroy() {
        onFirstPage = null // stops the paint check before the view is destroyed
        webView?.let {
            it.stopLoading()
            it.clearCache(true)
            it.destroy()
        }
        webView = null
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT < 34) fadeTransition(closing = true)
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val RETURN_SCHEME = "qullqi"
        private const val RETURN_HOST = "kyc"
        private const val PROVIDER_DOMAIN = "withpersona.com"
        private const val LOADER_TIMEOUT_MS = 20_000L // slow provider loads keep the loader, not a white page
        private const val PAINT_POLL_MS = 150L
        // Painted = what is on screen is themed: Persona first shows its own white loading layer
        // (with, in sandbox, a banner that has buttons and text), not necessarily on <body>. At 3
        // points down the middle, the first opaque background up from the visible element must not
        // be (near) white.
        // ponytail: assumes a dark Persona theme (Nocturne); a light theme waits for the timeout.
        private const val PAINTED_JS =
            "(function(){if(!document.body||!document.querySelector('button'))return false;" +
                "function bg(e){for(;e;e=e.parentElement){var c=getComputedStyle(e).backgroundColor;" +
                "var m=c.match(/[\\d.]+/g);if(m&&(m.length<4||+m[3]>0))return m}return null}" +
                "return [0.25,0.5,0.75].every(function(f){" +
                "var m=bg(document.elementFromPoint(innerWidth/2,innerHeight*f));" +
                "return m&&!(+m[0]>235&&+m[1]>235&&+m[2]>235)})})()"

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
