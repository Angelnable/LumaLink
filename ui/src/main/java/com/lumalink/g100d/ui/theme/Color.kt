package com.lumalink.g100d.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Transfer-only monochrome palette: pearl white / graphite only. No gold or
 * saturated accent is used by controls; error is the only muted color.
 */
val LumaPaper = Color(0xFFFDFDFB)
val LumaCanvas = Color(0xFFF8F8F5)
val LumaInk = Color(0xFF1B1C1E)
val LumaGraphite = Color(0xFF0F1012)
val LumaIvory = Color(0xFFF3F0E8)

val LightColors = lightColorScheme(
    primary = LumaInk,
    onPrimary = LumaPaper,
    primaryContainer = Color(0xFFE7E7E3),
    onPrimaryContainer = LumaInk,
    secondary = Color(0xFF5E5F61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9E5),
    onSecondaryContainer = LumaInk,
    tertiary = Color(0xFF6C6D6F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECECE8),
    onTertiaryContainer = LumaInk,
    error = Color(0xFF9B4A42),
    onError = Color.White,
    errorContainer = Color(0xFFF2DAD6),
    onErrorContainer = Color(0xFF3B0905),
    background = LumaCanvas,
    onBackground = LumaInk,
    surface = LumaPaper,
    onSurface = LumaInk,
    surfaceVariant = Color(0xFFE5E5E1),
    onSurfaceVariant = Color(0xFF6C6D6F),
    outline = Color(0xFFB8B8B3),
    outlineVariant = Color(0xFFD6D6D1),
    inverseSurface = Color(0xFF232427),
    inverseOnSurface = Color(0xFFF3F0E8),
    inversePrimary = LumaIvory,
    surfaceDim = Color(0xFFE5E5E1),
    surfaceBright = Color(0xFFFFFEFA),
    surfaceContainerLowest = Color(0xFFFFFEFA),
    surfaceContainerLow = Color(0xFFF3F3EF),
    surfaceContainer = Color(0xFFEEEEEA),
    surfaceContainerHigh = Color(0xFFE8E8E4),
    surfaceContainerHighest = Color(0xFFE1E1DD),
)

val DarkColors = darkColorScheme(
    primary = LumaIvory,
    onPrimary = Color(0xFF17181B),
    primaryContainer = Color(0xFF3A3B3E),
    onPrimaryContainer = LumaIvory,
    secondary = Color(0xFFB9B7B0),
    onSecondary = Color(0xFF17181B),
    secondaryContainer = Color(0xFF3A3B3E),
    onSecondaryContainer = Color(0xFFE8E5DE),
    tertiary = Color(0xFFA5A3A0),
    onTertiary = Color(0xFF17181B),
    tertiaryContainer = Color(0xFF333438),
    onTertiaryContainer = Color(0xFFE8E5DE),
    error = Color(0xFFD08B83),
    onError = Color(0xFF3B0905),
    errorContainer = Color(0xFF4A2420),
    onErrorContainer = Color(0xFFFFDAD4),
    background = LumaGraphite,
    onBackground = LumaIvory,
    surface = Color(0xFF17181B),
    onSurface = LumaIvory,
    surfaceVariant = Color(0xFF3A3B3E),
    onSurfaceVariant = Color(0xFFA5A3A0),
    outline = Color(0xFF5A5B5E),
    outlineVariant = Color(0xFF3A3B3E),
    inverseSurface = LumaIvory,
    inverseOnSurface = Color(0xFF17181B),
    inversePrimary = LumaInk,
    surfaceDim = Color(0xFF0B0C0E),
    surfaceBright = Color(0xFF333438),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF1D1E21),
    surfaceContainer = Color(0xFF232428),
    surfaceContainerHigh = Color(0xFF2A2B2F),
    surfaceContainerHighest = Color(0xFF333438),
)
