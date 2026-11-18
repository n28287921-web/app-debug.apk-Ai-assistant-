package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = VoidNavy,
    primaryContainer = DeepCobalt,
    onPrimaryContainer = IceCyan,
    secondary = ArcBlue,
    onSecondary = HudTextPrimary,
    secondaryContainer = ElevatedHudSurface,
    onSecondaryContainer = IceCyan,
    tertiary = EmeraldOnline,
    onTertiary = VoidNavy,
    background = VoidNavy,
    onBackground = HudTextPrimary,
    surface = DeepHudNavy,
    onSurface = HudTextPrimary,
    surfaceVariant = GlassNavySurface,
    onSurfaceVariant = HudTextSecondary,
    outline = HudOutline,
    outlineVariant = HudGlowBorder,
    error = CrimsonAlert,
    onError = HudTextPrimary
)

val JarvisShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun JarvisVipTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
