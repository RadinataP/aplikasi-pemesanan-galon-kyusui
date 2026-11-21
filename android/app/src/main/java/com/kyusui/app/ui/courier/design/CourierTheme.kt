package com.kyusui.app.ui.courier.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable

// Deklarasikan konstanta ini sebelum color scheme yang menggunakannya.
private val ColorTextSuccess = Color(0xFF126B3D)

private val CourierColorScheme = lightColorScheme(
    primary = CourierColors.Primary,
    onPrimary = CourierColors.White,
    primaryContainer = CourierColors.SurfaceBlue,
    onPrimaryContainer = CourierColors.Navy,

    secondary = CourierColors.Navy,
    onSecondary = CourierColors.White,
    secondaryContainer = CourierColors.SurfaceSoft,
    onSecondaryContainer = CourierColors.TextPrimary,

    tertiary = CourierColors.Success,
    onTertiary = CourierColors.White,
    tertiaryContainer = CourierColors.SuccessContainer,
    onTertiaryContainer = ColorTextSuccess,

    error = CourierColors.Error,
    onError = CourierColors.White,
    errorContainer = CourierColors.ErrorContainer,
    onErrorContainer = CourierColors.Error,

    background = CourierColors.Background,
    onBackground = CourierColors.TextPrimary,
    surface = CourierColors.Surface,
    onSurface = CourierColors.TextPrimary,
    surfaceVariant = CourierColors.Surface,
    onSurfaceVariant = CourierColors.TextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = CourierColors.NavyDeep,
    inverseOnSurface = CourierColors.White,
    inversePrimary = CourierColors.Aqua,

    outline = CourierColors.Outline,
    outlineVariant = CourierColors.Divider,
    scrim = Color.Black
)

private val CourierTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 33.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 29.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

private val CourierShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Theme terisolasi untuk route Kurir.
 * Jangan membungkus seluruh AppNavHost dengan CourierTheme.
 */
@Composable
fun CourierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CourierColorScheme,
        typography = CourierTypography,
        shapes = CourierShapes,
        content = content
    )
}
