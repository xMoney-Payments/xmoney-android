package com.xmoney.example

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object DemoColors {
    val Navy = Color(0xFF0B1F33)
    val NavyMuted = Color(0xFF1A3348)
    val Accent = Color(0xFF0E7C66)
    val AccentPressed = Color(0xFF0A5C4C)
    val Paper = Color(0xFFF3EDE3)
    val PaperElevated = Color(0xFFFFFBF5)
    val Ink = Color(0xFF14202B)
    val InkMuted = Color(0xFF5C6B78)
    val Line = Color(0xFFD9D0C3)
    val Danger = Color(0xFFB42318)
    val Success = Color(0xFF0E7C66)
    val OnAccent = Color(0xFFFFFFFF)
}

private val DemoLightScheme = lightColorScheme(
    primary = DemoColors.Navy,
    onPrimary = Color.White,
    primaryContainer = DemoColors.NavyMuted,
    onPrimaryContainer = Color.White,
    secondary = DemoColors.Accent,
    onSecondary = DemoColors.OnAccent,
    secondaryContainer = Color(0xFFD8F3EC),
    onSecondaryContainer = DemoColors.AccentPressed,
    background = DemoColors.Paper,
    onBackground = DemoColors.Ink,
    surface = DemoColors.PaperElevated,
    onSurface = DemoColors.Ink,
    surfaceVariant = Color(0xFFE8DFD2),
    onSurfaceVariant = DemoColors.InkMuted,
    outline = DemoColors.Line,
    error = DemoColors.Danger,
    onError = Color.White,
)

private val DemoDarkScheme = darkColorScheme(
    primary = Color(0xFF9EC0DF),
    onPrimary = DemoColors.Navy,
    secondary = Color(0xFF6FD4BE),
    onSecondary = DemoColors.Navy,
    background = Color(0xFF0E1620),
    onBackground = Color(0xFFF2EDE6),
    surface = Color(0xFF162232),
    onSurface = Color(0xFFF2EDE6),
    surfaceVariant = Color(0xFF243447),
    onSurfaceVariant = Color(0xFFB7C3CE),
    outline = Color(0xFF3A4D61),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val DemoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DemoDarkScheme else DemoLightScheme,
        typography = DemoTypography,
        content = content,
    )
}
