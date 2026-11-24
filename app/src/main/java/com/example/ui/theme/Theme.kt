package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PixelWorldColorScheme =
  darkColorScheme(
    primary = TacticalCyan,
    onPrimary = DeepSpaceBg,
    primaryContainer = HudSurfaceHighlight,
    onPrimaryContainer = TextPrimary,
    secondary = TacticalAmber,
    onSecondary = DeepSpaceBg,
    secondaryContainer = HudSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = LifeEmerald,
    onTertiary = DeepSpaceBg,
    error = WarCrimson,
    onError = TextPrimary,
    background = DeepSpaceBg,
    onBackground = TextPrimary,
    surface = HudSurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = HudSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = HudBorder,
    outlineVariant = HudBorderBright,
  )

private val PixelWorldShapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
  )

@Composable
fun PixelWorldTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = PixelWorldColorScheme,
    typography = PixelWorldTypography,
    shapes = PixelWorldShapes,
    content = content,
  )
}
