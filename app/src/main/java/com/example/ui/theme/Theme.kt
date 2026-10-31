package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightMarketColorScheme = lightColorScheme(
    primary = MarketGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = MarketGreenLight,
    onPrimaryContainer = MarketGreenDark,
    secondary = MarketSecondary,
    onSecondary = Color.White,
    secondaryContainer = MarketSecondaryContainer,
    onSecondaryContainer = MarketGreenDark,
    tertiary = HarvestAmber,
    onTertiary = Color.White,
    tertiaryContainer = HarvestAmberContainer,
    onTertiaryContainer = Color(0xFF78350F),
    background = MarketBackground,
    onBackground = MarketTextPrimary,
    surface = MarketWhite,
    onSurface = MarketTextPrimary,
    surfaceVariant = MarketGreenSurfaceTint,
    onSurfaceVariant = MarketTextSecondary,
    outline = MarketCardBorder,
    outlineVariant = Color(0xFFE4EFE7),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkMarketColorScheme = darkColorScheme(
    primary = MarketGreenDarkPrimary,
    onPrimary = MarketGreenDark,
    primaryContainer = MarketGreenDarkContainer,
    onPrimaryContainer = MarketGreenLight,
    secondary = Color(0xFF86EFAC),
    onSecondary = MarketGreenDark,
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = MarketGreenLight,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = HarvestAmberContainer,
    background = MarketDarkBackground,
    onBackground = Color(0xFFECFDF5),
    surface = MarketDarkSurface,
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF1B3124),
    onSurfaceVariant = Color(0xFFB7D4C2),
    outline = Color(0xFF2D4F3B),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val MarketShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Clean white-and-green wholesale market theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkMarketColorScheme else LightMarketColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MarketShapes,
        content = content
    )
}
