package com.codewave.qullqi.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QSize
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import qullqi.shared.generated.resources.Res
import qullqi.shared.generated.resources.ph_arrow_down
import qullqi.shared.generated.resources.ph_arrow_up
import qullqi.shared.generated.resources.ph_backspace
import qullqi.shared.generated.resources.ph_caret_left
import qullqi.shared.generated.resources.ph_check
import qullqi.shared.generated.resources.ph_compass
import qullqi.shared.generated.resources.ph_device_mobile
import qullqi.shared.generated.resources.ph_fingerprint
import qullqi.shared.generated.resources.ph_identification_card
import qullqi.shared.generated.resources.ph_lock_simple
import qullqi.shared.generated.resources.ph_paper_plane_tilt
import qullqi.shared.generated.resources.ph_seal_check
import qullqi.shared.generated.resources.ph_sign_out
import qullqi.shared.generated.resources.ph_user_switch
import qullqi.shared.generated.resources.ph_warning
import qullqi.shared.generated.resources.ph_x

/** Phosphor Icons, regular weight (STYLEGUIDE §4; MIT, docs/licenses/Phosphor-MIT.txt). */
object Ph {
    val ArrowDown = Res.drawable.ph_arrow_down
    val ArrowUp = Res.drawable.ph_arrow_up
    val Backspace = Res.drawable.ph_backspace
    val CaretLeft = Res.drawable.ph_caret_left
    val Check = Res.drawable.ph_check
    val Compass = Res.drawable.ph_compass
    val DeviceMobile = Res.drawable.ph_device_mobile
    val Fingerprint = Res.drawable.ph_fingerprint
    val IdentificationCard = Res.drawable.ph_identification_card
    val LockSimple = Res.drawable.ph_lock_simple
    val PaperPlaneTilt = Res.drawable.ph_paper_plane_tilt
    val SealCheck = Res.drawable.ph_seal_check
    val SignOut = Res.drawable.ph_sign_out
    val UserSwitch = Res.drawable.ph_user_switch
    val Warning = Res.drawable.ph_warning
    val X = Res.drawable.ph_x
}

@Composable
fun QIcon(
    icon: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = QSize.iconBar,
    tint: Color = Q.colors.tx,
) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}
