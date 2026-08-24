package com.xmoney.example.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.xmoney.paymentelement.R as PaymentElementR

val ExampleFontFamily: FontFamily = FontFamily(
    Font(PaymentElementR.font.roobert_regular, FontWeight.Normal),
    Font(PaymentElementR.font.roobert_medium, FontWeight.Medium),
    Font(PaymentElementR.font.roobert_semibold, FontWeight.SemiBold),
    Font(PaymentElementR.font.roobert_bold, FontWeight.Bold),
)

val ExampleShapes = Shapes(
    extraSmall = RoundedCornerShape(ExampleRadii.small),
    small = RoundedCornerShape(ExampleRadii.small),
    medium = RoundedCornerShape(ExampleRadii.inner),
    large = RoundedCornerShape(ExampleRadii.card),
    extraLarge = RoundedCornerShape(ExampleRadii.card),
)

object ExampleRadii {
    val pill = 9999.dp
    val card = 24.dp
    val inner = 16.dp
    val small = 8.dp
}

/**
 * Process-wide light/dark preference for example-app chrome.
 * Also forces SDK [com.xmoney.payments.config.UserInterfaceStyle] on Sheet, Element, and Google Pay.
 */
object ExampleThemeController {
    private const val PREFS = "xmoney_example"
    private const val KEY_DARK = "dark_theme"

    internal val darkOverride = mutableStateOf<Boolean?>(null)

    fun initialize(context: Context, systemDark: Boolean) {
        if (darkOverride.value != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkOverride.value = if (prefs.contains(KEY_DARK)) {
            prefs.getBoolean(KEY_DARK, systemDark)
        } else {
            systemDark
        }
    }

    fun isDark(systemDark: Boolean): Boolean = darkOverride.value ?: systemDark

    fun toggle(context: Context, systemDark: Boolean) {
        setDark(context, !isDark(systemDark))
    }

    fun setDark(context: Context, dark: Boolean) {
        if (darkOverride.value == dark) return
        darkOverride.value = dark
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
    }
}

data class ExampleSemanticColors(
    val success: Color,
    val successSoft: Color,
    val dangerSoft: Color,
    val lime: Color,
    val limeDark: Color,
    val hairline: Color,
)

val LocalExampleSemantics = staticCompositionLocalOf {
    ExampleSemanticColors(
        success = ExampleColors.Success,
        successSoft = ExampleColors.SuccessSoftLight,
        dangerSoft = ExampleColors.DangerSoftLight,
        lime = ExampleColors.Lime,
        limeDark = ExampleColors.LimeDark,
        hairline = ExampleColors.LightHairline,
    )
}

/** Store chrome accent (Pay / Add). Defaults to xMoney purple. */
val LocalBrandAccent = staticCompositionLocalOf { ExampleColors.Purple }

val LocalBrandOnAccent = staticCompositionLocalOf { Color.White }

/** Accent used as text on surface (must stay readable). Defaults to [LocalBrandAccent]. */
val LocalBrandAccentText = staticCompositionLocalOf { ExampleColors.Purple }

private val ExampleTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = ExampleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
)

private val LightScheme = lightColorScheme(
    primary = ExampleColors.Purple,
    onPrimary = Color.White,
    primaryContainer = ExampleColors.PurpleSoftLight,
    onPrimaryContainer = ExampleColors.Purple,
    secondary = ExampleColors.Lime,
    onSecondary = ExampleColors.LimeDark,
    secondaryContainer = ExampleColors.Lime,
    onSecondaryContainer = ExampleColors.LimeDark,
    background = ExampleColors.LightBg,
    onBackground = ExampleColors.LightText,
    surface = ExampleColors.LightCard,
    onSurface = ExampleColors.LightText,
    surfaceVariant = ExampleColors.LightBg,
    onSurfaceVariant = ExampleColors.LightMuted,
    outline = ExampleColors.LightHairline,
    outlineVariant = ExampleColors.LightHairline,
    error = ExampleColors.Error,
    onError = Color.White,
    errorContainer = ExampleColors.DangerSoftLight,
    onErrorContainer = ExampleColors.Error,
)

private val DarkScheme = darkColorScheme(
    primary = ExampleColors.Purple,
    onPrimary = Color.White,
    primaryContainer = ExampleColors.PurpleSoftDark,
    onPrimaryContainer = ExampleColors.PurpleElectric,
    secondary = ExampleColors.Lime,
    onSecondary = ExampleColors.LimeDark,
    secondaryContainer = ExampleColors.Lime,
    onSecondaryContainer = ExampleColors.LimeDark,
    background = ExampleColors.DarkBg,
    onBackground = ExampleColors.DarkText,
    surface = ExampleColors.DarkCard,
    onSurface = ExampleColors.DarkText,
    surfaceVariant = ExampleColors.DarkElevated,
    onSurfaceVariant = ExampleColors.DarkMuted,
    outline = ExampleColors.DarkHairline,
    outlineVariant = ExampleColors.DarkHairline,
    error = ExampleColors.Error,
    onError = Color.White,
    errorContainer = ExampleColors.DangerSoftDark,
    onErrorContainer = ExampleColors.Error,
)

@Composable
fun ExampleTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    ExampleThemeController.initialize(context, systemDark)
    val darkTheme = ExampleThemeController.isDark(systemDark)

    val semantics = if (darkTheme) {
        ExampleSemanticColors(
            success = ExampleColors.Success,
            successSoft = ExampleColors.SuccessSoftDark,
            dangerSoft = ExampleColors.DangerSoftDark,
            lime = ExampleColors.Lime,
            limeDark = ExampleColors.LimeDark,
            hairline = ExampleColors.DarkHairline,
        )
    } else {
        ExampleSemanticColors(
            success = ExampleColors.Success,
            successSoft = ExampleColors.SuccessSoftLight,
            dangerSoft = ExampleColors.DangerSoftLight,
            lime = ExampleColors.Lime,
            limeDark = ExampleColors.LimeDark,
            hairline = ExampleColors.LightHairline,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExampleSemantics provides semantics) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ExampleTypography,
            shapes = ExampleShapes,
            content = content,
        )
    }
}
