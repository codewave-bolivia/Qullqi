package com.codewave.qullqi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.codewave.qullqi.domain.system.PageOpen
import com.codewave.qullqi.domain.system.ProviderPage
import kotlinx.coroutines.CompletableDeferred

/**
 * Spec 005 FR-005b: the provider page opens only with the camera allowed. Must be created in
 * [ComponentActivity.onCreate] (activity-result launchers are registered before STARTED).
 */
class AndroidProviderPage(private val activity: ComponentActivity) : ProviderPage {

    private var pending: CompletableDeferred<Boolean>? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.complete(granted)
        pending = null
    }

    /** Set by the page's result (delivered before the activity resumes), read once by the flow. */
    private var finished = false

    private val page = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        finished = result.resultCode == Activity.RESULT_OK
    }

    override fun consumeFinished(): Boolean = finished.also { finished = false }

    override suspend fun open(url: String): PageOpen {
        if (!hasCamera()) {
            val answer = CompletableDeferred<Boolean>().also { pending = it }
            launcher.launch(Manifest.permission.CAMERA)
            if (!answer.await()) {
                // After a denial, no rationale means "don't ask again": only Settings can grant it.
                val permanent = !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                return PageOpen.CameraDenied(permanent)
            }
        }
        finished = false
        page.launch(KycWebViewActivity.intent(activity, url))
        if (Build.VERSION.SDK_INT < 34) activity.fadeTransition()
        return PageOpen.Opened
    }

    override fun openCameraSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
