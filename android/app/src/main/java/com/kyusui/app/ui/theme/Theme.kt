package com.kyusui.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Bright default scheme: the app intentionally opens in a light, airy appearance. */
val KyusuiLightColorScheme = lightColorScheme(
    primary = KyusuiColors.Primary, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3FA), onPrimaryContainer = Color(0xFF07506D),
    secondary = KyusuiColors.Secondary, onSecondary = Color(0xFF063F42),
    secondaryContainer = Color(0xFFDDF7F3), onSecondaryContainer = Color(0xFF145B59),
    tertiary = KyusuiColors.Info, onTertiary = Color.White,
    tertiaryContainer = KyusuiColors.InfoContainer, onTertiaryContainer = Color(0xFF174F70),
    error = KyusuiColors.Error, onError = Color.White,
    errorContainer = KyusuiColors.ErrorContainer, onErrorContainer = Color(0xFF7B252D),
    background = KyusuiColors.Background, onBackground = KyusuiColors.TextPrimary,
    surface = KyusuiColors.Surface, onSurface = KyusuiColors.TextPrimary,
    surfaceVariant = Color(0xFFECF5F8), onSurfaceVariant = KyusuiColors.TextSecondary,
    outline = KyusuiColors.Outline, outlineVariant = Color(0xFFE4EEF2),
    scrim = Color.Black, inverseSurface = Color(0xFF21495C),
    inverseOnSurface = Color(0xFFF5FBFD), inversePrimary = Color(0xFF9DDFEF),
    surfaceTint = KyusuiColors.Primary
)

/** Retained for components that explicitly opt into dark mode; not the customer default. */
val KyusuiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DD8EA), onPrimary = Color(0xFF073F55),
    primaryContainer = Color(0xFF075A78), onPrimaryContainer = Color(0xFFDDF3FA),
    secondary = Color(0xFF8BE2D8), onSecondary = Color(0xFF064344),
    secondaryContainer = Color(0xFF17645F), onSecondaryContainer = Color(0xFFDDF7F3),
    tertiary = Color(0xFF9BCFEA), onTertiary = Color(0xFF174F70),
    tertiaryContainer = Color(0xFF245E7A), onTertiaryContainer = Color(0xFFE4F4FB),
    error = Color(0xFFFFB4B4), onError = Color(0xFF7B252D),
    errorContainer = Color(0xFF8F333B), onErrorContainer = Color(0xFFFFE9E9),
    background = Color(0xFF132D3B), onBackground = Color(0xFFEAF5F8),
    surface = Color(0xFF183746), onSurface = Color(0xFFEAF5F8),
    surfaceVariant = Color(0xFF254655), onSurfaceVariant = Color(0xFFBBD0D9),
    outline = Color(0xFF718E9A), outlineVariant = Color(0xFF385766),
    scrim = Color.Black, inverseSurface = Color(0xFFEAF5F8),
    inverseOnSurface = Color(0xFF183746), inversePrimary = KyusuiColors.Primary,
    surfaceTint = Color(0xFF8DD8EA)
)
