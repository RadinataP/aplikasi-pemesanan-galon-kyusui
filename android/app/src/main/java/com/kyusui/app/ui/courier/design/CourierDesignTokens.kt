package com.kyusui.app.ui.courier.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Courier-only visual system. Neutral surfaces are deliberate:
 * white cards on a cool neutral canvas, teal for primary actions,
 * slate for typography, and semantic colors only for status.
 */
object CourierColors {
    val Primary = Color(0xFF079DA5)
    val PrimaryPressed = Color(0xFF087F86)
    val Navy = Color(0xFF153B50)
    val NavyDeep = Color(0xFF102F41)
    val Aqua = Color(0xFF28B9B4)

    val Background = Color(0xFFF5F8FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFF0F8F8)
    val SurfaceBlue = Color(0xFFE5F5F4)
    val SurfaceNavy = Color(0xFF153B50)

    val TextPrimary = Color(0xFF183746)
    val TextSecondary = Color(0xFF687E89)
    val Outline = Color(0xFFE1E9ED)
    val Divider = Color(0xFFEDF1F3)

    val Success = Color(0xFF168653)
    val SuccessContainer = Color(0xFFE7F5EC)
    val Warning = Color(0xFF9A650D)
    val WarningContainer = Color(0xFFFFF5E2)
    val Error = Color(0xFFB93845)
    val ErrorContainer = Color(0xFFFFECEE)
    val Info = Color(0xFF176F91)
    val InfoContainer = Color(0xFFEAF4F8)

    val White = Color.White
}

object CourierSpacing {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Screen = 20.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 40.dp
}

object CourierDimensions {
    val MinimumTouchTarget = 48.dp
    val ButtonHeight = 52.dp
    val InputHeight = 54.dp
    val CardRadius = 18.dp
    val ControlRadius = 14.dp
    val BadgeRadius = 50.dp
    val CardElevation = 0.dp
    val ScreenMaxWidth = 560.dp
}
