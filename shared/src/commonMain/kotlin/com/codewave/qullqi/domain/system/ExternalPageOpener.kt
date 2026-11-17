package com.codewave.qullqi.domain.system

import com.codewave.qullqi.domain.model.SimpleDate

/** Opens a provider page outside the app's screens (Android: Custom Tab, spec 005 FR-005a). */
fun interface ExternalPageOpener {
    fun open(url: String)
}

/** For platforms without a page opener yet (iOS/Desktop: Builder). */
object NoPageOpener : ExternalPageOpener {
    override fun open(url: String) = Unit
}

/** Today's date on the device, for the age check (spec 005 research R5). */
fun interface Today {
    fun date(): SimpleDate
}
