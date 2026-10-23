package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Standard Material Design 3 solid color fallback schemes
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF67D8FF),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFFB4CAD6),
    onSecondary = Color(0xFF1E333D),
    secondaryContainer = Color(0xFF354A54),
    onSecondaryContainer = Color(0xFFD0E6F2),
    tertiary = Color(0xFFC7C2EA),
    onTertiary = Color(0xFF302C4C),
    background = Color(0xFF0E1418),
    onBackground = Color(0xFFDEE3E7),
    surface = Color(0xFF161C21),
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF222930),
    onSurfaceVariant = Color(0xFFC0C8CD),
    outline = Color(0xFF8A9297),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006686),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE9FF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = Color(0xFF4C616C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E6F2),
    onSecondaryContainer = Color(0xFF081E27),
    tertiary = Color(0xFF5E5A7D),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF0F4F7),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE2E9EE),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun FakeStandbyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
