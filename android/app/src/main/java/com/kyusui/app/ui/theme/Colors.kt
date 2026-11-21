package com.kyusui.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Bright, water-inspired palette for the Berkah Water customer experience. */
object KyusuiColors {
    val Primary = Color(0xFF087FA8)
    val Secondary = Color(0xFF20B8B0)
    val Background = Color(0xFFF3FAFC)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF163B50)
    val TextSecondary = Color(0xFF627D8D)
    val Disabled = Color(0xFFA5B8C2)
    val Outline = Color(0xFFD8E8EE)

    val Success = Color(0xFF168B69)
    val SuccessContainer = Color(0xFFE2F6EF)
    val Warning = Color(0xFFB97819)
    val WarningContainer = Color(0xFFFFF3D9)
    val Error = Color(0xFFD45158)
    val ErrorContainer = Color(0xFFFFE9E9)
    val Info = Color(0xFF247FAE)
    val InfoContainer = Color(0xFFE4F4FB)

    // Compatibility aliases for the existing Owner/Courier modules.
    val CustomerPrimary = Primary
    val OwnerPrimary = Primary
    val CourierPrimary = Primary
    val StatusPending = Warning
    val StatusProcessing = Info
    val StatusCompleted = Success
    val StatusCancelled = Error
    val StatusWaitingVerification = Warning
    val PaymentQris = Primary
    val PaymentCash = Secondary
    val SurfaceContainerHigh = Color(0xFFEAF4F7)
    val SurfaceContainerHighest = Color(0xFFDDECF1)
}
