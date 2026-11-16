package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class StudioPalette(
    val voidBg: Color = ObsidianVoid,
    val studioSlate: Color = StudioSlate,
    val panelSurface: Color = PanelSurface,
    val elevatedTrack: Color = ElevatedTrack,
    val hairlineBorder: Color = HairlineBorder,
    val playheadCyan: Color = ElectricCyan,
    val audioAmber: Color = WarmAmber,
    val cutCrimson: Color = CoralCrimson,
    val readyEmerald: Color = EmeraldReady,
    val gradeViolet: Color = VioletGrade,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted,
    val cyanMuted: Color = CyanMuted,
    val amberMuted: Color = AmberMuted,
    val crimsonMuted: Color = CrimsonMuted
)

val LocalStudioPalette = staticCompositionLocalOf { StudioPalette() }

private val CutForgeDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = CyanMuted,
    onPrimaryContainer = ElectricCyan,
    secondary = WarmAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = AmberMuted,
    onSecondaryContainer = WarmAmber,
    tertiary = VioletGrade,
    onTertiary = TextPrimary,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = StudioSlate,
    onSurface = TextPrimary,
    surfaceVariant = PanelSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = StudioSlate,
    surfaceContainer = PanelSurface,
    surfaceContainerHigh = ElevatedTrack,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = CoralCrimson,
    onError = TextPrimary,
    errorContainer = CrimsonMuted,
    onErrorContainer = CoralCrimson
)

val CutForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun CutForgeTheme(
    content: @Composable () -> Unit
) {
    val palette = StudioPalette()
    CompositionLocalProvider(LocalStudioPalette provides palette) {
        MaterialTheme(
            colorScheme = CutForgeDarkColorScheme,
            typography = CutForgeTypography,
            shapes = CutForgeShapes,
            content = content
        )
    }
}
