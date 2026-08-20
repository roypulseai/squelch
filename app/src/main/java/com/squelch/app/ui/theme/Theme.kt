package com.squelch.app.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = DarkBackground,
    primaryContainer = AccentDark,
    onPrimaryContainer = DarkOnSurface,
    secondary = AccentLight,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = Accent,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOnSurfaceVariant,
    error = ErrorDark,
    onError = DarkBackground,
    errorContainer = Color(0xFF3D1A1A),
    onErrorContainer = Color(0xFFFFB4AB),
)

private val LightColorScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = LightSurface,
    primaryContainer = Accent,
    onPrimaryContainer = LightOnSurface,
    secondary = Accent,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = AccentDark,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOnSurfaceVariant,
    error = ErrorLight,
    onError = LightSurface,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun SquelchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
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
