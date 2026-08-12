package com.squelch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PhosphorGreen = Color(0xFF00FF41)
val PhosphorDim = Color(0xFF0D5C1E)
val Amber = Color(0xFFFFB000)
val ScreenBlack = Color(0xFF0A0A12)
val PanelBlack = Color(0xFF11111C)
val BorderGreen = Color(0xFF1F6B31)

private val RetroColors = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = ScreenBlack,
    secondary = Amber,
    onSecondary = ScreenBlack,
    background = ScreenBlack,
    onBackground = PhosphorGreen,
    surface = PanelBlack,
    onSurface = PhosphorGreen,
    surfaceVariant = PanelBlack,
    onSurfaceVariant = PhosphorDim,
    outline = BorderGreen,
    error = Color(0xFFFF3B3B)
)

private val RetroTypography = androidx.compose.material3.Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.sp
    )
)

@Composable
fun SquelchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RetroColors,
        typography = RetroTypography,
        content = content
    )
}
