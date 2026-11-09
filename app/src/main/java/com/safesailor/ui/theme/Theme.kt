package com.safesailor.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF062A63),
    secondary = Color(0xFF4A6FA5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECFA),
    onSecondaryContainer = Color(0xFF13305A),
    tertiary = Color(0xFF0E8A7E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F3EE),
    onTertiaryContainer = Color(0xFF00382F),
    background = Color.White,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF3FB),
    onSurfaceVariant = Color(0xFF5A6578),
    surfaceTint = Color(0xFF1565D8),
    outline = Color(0xFFC4CEDD),
    outlineVariant = Color(0xFFE2E8F2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFE),
    surfaceContainer = Color(0xFFF3F6FC),
    surfaceContainerHigh = Color(0xFFEDF2FA),
    surfaceContainerHighest = Color(0xFFE6EDF8),
    error = Color(0xFFD33A2C),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E4),
    onErrorContainer = Color(0xFF5F120B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF002B66),
    primaryContainer = Color(0xFF1A3F7A),
    onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFFAFC6EA),
    onSecondary = Color(0xFF1A2F4F),
    secondaryContainer = Color(0xFF22344F),
    onSecondaryContainer = Color(0xFFD6E3F8),
    tertiary = Color(0xFF7FD8CB),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF0F4A42),
    onTertiaryContainer = Color(0xFFC8F2EA),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE4E9F2),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE4E9F2),
    surfaceVariant = Color(0xFF1B263A),
    onSurfaceVariant = Color(0xFF9EABC0),
    surfaceTint = Color(0xFF8AB4FF),
    outline = Color(0xFF3B4A63),
    outlineVariant = Color(0xFF243149),
    surfaceContainerLowest = Color(0xFF070D18),
    surfaceContainerLow = Color(0xFF0F1828),
    surfaceContainer = Color(0xFF131D2F),
    surfaceContainerHigh = Color(0xFF182437),
    surfaceContainerHighest = Color(0xFF1E2B40),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F120B),
    errorContainer = Color(0xFF4A1A16),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SafeSailorTheme(dark: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
