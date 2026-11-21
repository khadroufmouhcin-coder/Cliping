package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = CrispWhite,
    primaryContainer = DeepVioletContainer,
    onPrimaryContainer = ElectricVioletBright,
    secondary = NeonCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF0E3947),
    onSecondaryContainer = NeonCyan,
    tertiary = ViralYellow,
    onTertiary = ObsidianBg,
    tertiaryContainer = Color(0xFF423606),
    onTertiaryContainer = ViralYellow,
    background = ObsidianBg,
    onBackground = CrispWhite,
    surface = StudioSurface,
    onSurface = CrispWhite,
    surfaceVariant = DeckContainer,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = DeckContainer,
    surfaceContainerHigh = ElevatedContainer,
    outline = HairlineBorder,
    outlineVariant = Color(0xFF251F42),
    error = PeakRed,
    onError = CrispWhite
)

private val StudioLightColorScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightContainer,
    onSurfaceVariant = LightMuted,
    outline = Color(0xFFD5CFF0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ViralClipperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ViralClipperTypography,
        shapes = StudioShapes,
        content = content
    )
}
