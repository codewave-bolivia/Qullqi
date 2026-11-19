package com.codewave.qullqi.domain.system

/**
 * Opens the identity provider's page inside the app (spec 005 FR-005a/b; Android: camera
 * permission, then a WebView screen). The app never stores or logs the page address.
 */
interface ProviderPage {
    suspend fun open(url: String): PageOpen

    /** Opens the system settings of the app so the user can allow the camera. */
    fun openCameraSettings()
}

sealed interface PageOpen {
    data object Opened : PageOpen

    /** [permanent]: the user chose "don't ask again"; only the system settings can grant it. */
    data class CameraDenied(val permanent: Boolean) : PageOpen
}

/** For platforms without a provider page yet (iOS/Desktop: Builder). */
object NoProviderPage : ProviderPage {
    override suspend fun open(url: String): PageOpen = PageOpen.Opened
    override fun openCameraSettings() = Unit
}

