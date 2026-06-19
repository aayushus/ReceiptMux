package com.scantoftp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand seed (kept for reference / widgets that need a single accent).
val TealSeed = Color(0xFF5B5BD6)

/**
 * Vibrant accent palette used for gradients, stat tiles, and colorful highlights
 * across the app. These read well on both light and dark surfaces.
 */
object Brand {
    val Violet = Color(0xFF7C5CFC)
    val Indigo = Color(0xFF5B5BD6)
    val Blue = Color(0xFF3B82F6)
    val Sky = Color(0xFF38BDF8)
    val Cyan = Color(0xFF22D3EE)
    val Teal = Color(0xFF14B8A6)
    val Emerald = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Orange = Color(0xFFFB7185)
    val Rose = Color(0xFFF43F5E)
    val Pink = Color(0xFFEC4899)
}

object BrandGradients {
    val Hero = Brush.linearGradient(listOf(Brand.Violet, Brand.Indigo, Brand.Sky))
    val Success = Brush.linearGradient(listOf(Brand.Teal, Brand.Emerald))
    val Sunset = Brush.linearGradient(listOf(Brand.Pink, Brand.Orange, Brand.Amber))
    val Ocean = Brush.linearGradient(listOf(Brand.Indigo, Brand.Blue, Brand.Cyan))
}

// --- Light ---
private val LightPrimary = Color(0xFF5B40E0)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFE5DEFF)
private val LightOnPrimaryContainer = Color(0xFF1B0B66)
private val LightSecondary = Color(0xFF6D5BD0)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFEADEFF)
private val LightOnSecondaryContainer = Color(0xFF24165E)
private val LightTertiary = Color(0xFF0EA5A0)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightBackground = Color(0xFFFBFAFF)
private val LightOnBackground = Color(0xFF1A1A22)
private val LightSurface = Color(0xFFFBFAFF)
private val LightOnSurface = Color(0xFF1A1A22)
private val LightSurfaceVariant = Color(0xFFE4E1EC)
private val LightOnSurfaceVariant = Color(0xFF47465A)
private val LightOutline = Color(0xFF78768A)
private val LightOutlineVariant = Color(0xFFC9C5D8)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

// --- Dark ---
private val DarkPrimary = Color(0xFFC9BEFF)
private val DarkOnPrimary = Color(0xFF2A1788)
private val DarkPrimaryContainer = Color(0xFF463BC0)
private val DarkOnPrimaryContainer = Color(0xFFE5DEFF)
private val DarkSecondary = Color(0xFFCFC2FF)
private val DarkOnSecondary = Color(0xFF31206E)
private val DarkSecondaryContainer = Color(0xFF453689)
private val DarkOnSecondaryContainer = Color(0xFFEADEFF)
private val DarkTertiary = Color(0xFF54E0D6)
private val DarkOnTertiary = Color(0xFF00382F)
private val DarkBackground = Color(0xFF121218)
private val DarkOnBackground = Color(0xFFE5E1E9)
private val DarkSurface = Color(0xFF121218)
private val DarkOnSurface = Color(0xFFE5E1E9)
private val DarkSurfaceVariant = Color(0xFF47465A)
private val DarkOnSurfaceVariant = Color(0xFFC9C5D8)
private val DarkOutline = Color(0xFF928F9F)
private val DarkOutlineVariant = Color(0xFF47465A)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3FB),
    surfaceContainer = Color(0xFFEFEDF8),
    surfaceContainerHigh = Color(0xFFE9E7F3),
    surfaceContainerHighest = Color(0xFFE4E1EC),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1A1A22),
    surfaceContainer = Color(0xFF1E1E27),
    surfaceContainerHigh = Color(0xFF292931),
    surfaceContainerHighest = Color(0xFF34343C),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)
