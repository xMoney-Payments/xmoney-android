package com.xmoney.paymentelement.theme

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmoney.payments.config.AppearanceColors
import com.xmoney.payments.config.ResolvedPaymentConfig

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
data class CheckoutTheme(
    val primary: Color,
    val background: Color,
    val componentBackground: Color,
    val componentBorder: Color,
    val componentDivider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val componentText: Color,
    val placeholderText: Color,
    val icon: Color,
    val error: Color,
    val borderRadius: Dp,
    val borderWidth: Dp,
    val fontScale: Float,
    val fontFamily: FontFamily = PaymentFontFamily.family,
    val primaryButtonFontFamily: FontFamily = PaymentFontFamily.family,
    val primaryButtonBackground: Color,
    val primaryButtonText: Color,
    val primaryButtonBorder: Color,
    val primaryButtonBorderRadius: Dp,
    val primaryButtonBorderWidth: Dp,
    val selectedBackground: Color,
    val accentIconBackground: Color,
    val containerBorder: Color,
    val neutralChip: Color,
    val fieldBorder: Color,
    val fieldDivider: Color,
    val mutedIcon: Color,
    val unselectedRing: Color,
    val errorBorder: Color,
    val errorText: Color,
    val footerBorder: Color,
    val sheetHandle: Color,
    val orDivider: Color,
    val visaMarkTint: Color?,
    val paymentContainerRadius: Dp,
    val formFieldRadius: Dp,
    val formFieldHeight: Dp,
) {
    val text: Color get() = primaryText
    val fieldBackground: Color get() = componentBackground
    val border: Color get() = componentBorder
    val cornerRadius: Dp get() = borderRadius

    val containerBorderWidth: Dp
        get() = if (containerBorder.alpha == 0f) 0.dp else 1.dp

    fun fieldStrokeWidth(hasError: Boolean): Dp =
        if (hasError) maxOf(borderWidth, 1.5.dp) else borderWidth

    fun scaledSp(size: Float): TextUnit = (size * fontScale).sp

    companion object {
        val BrandPrimary = Color(0xFF7C4DFF)
        const val DefaultPrimaryButtonRadius = 9999f
        const val DefaultFormFieldRadius = 16f
        const val DefaultPaymentContainerRadius = 20f
        private val DefaultErrorBorder = Color(0xFFEF4444)
        private val DefaultErrorText = Color(0xFFDC2626)
        private val LightInk = Color(0xFF16141A)
        private val DarkInk = Color(0xFFF7F6F9)
        private val DarkChrome = Color(0xFFFFFFFF)

        fun resolve(config: ResolvedPaymentConfig, isDark: Boolean): CheckoutTheme {
            val appearance = config.options.appearance
            val defaults = if (isDark) DefaultColors.dark else DefaultColors.light
            val modeColors = if (isDark) appearance.colorsDark else appearance.colorsLight
            val sharedColors = appearance.colors
            val ink = if (isDark) DarkInk else LightInk

            fun color(
                selector: (AppearanceColors) -> String?,
                fallback: Color,
            ): Color {
                val hex = modeColors?.let(selector) ?: sharedColors?.let(selector)
                return parseColor(hex) ?: fallback
            }

            fun optionalColor(
                selector: (AppearanceColors) -> String?,
                fallback: Color,
            ): Color {
                val raw = modeColors?.let(selector) ?: sharedColors?.let(selector)
                return parseColorOrNone(raw) ?: fallback
            }

            fun inkAlpha(alpha: Int): Color = ink.copy(alpha = alpha / 255f)

            val pbMode = if (isDark) appearance.primaryButton?.colorsDark else appearance.primaryButton?.colorsLight
            val pbShared = appearance.primaryButton?.colors
            val pbShapes = appearance.primaryButton

            val primary = color({ it.primary }, defaults.primary)
            val icon = color({ it.icon }, defaults.icon)
            fun chromeAlpha(alpha: Int): Color =
                (if (isDark) DarkChrome else ink).copy(alpha = alpha / 255f)

            return CheckoutTheme(
                primary = primary,
                background = color({ it.background }, defaults.background),
                componentBackground = color({ it.componentBackground }, defaults.componentBackground),
                componentBorder = color({ it.componentBorder }, defaults.componentBorder),
                componentDivider = color({ it.componentDivider }, defaults.componentDivider),
                primaryText = color({ it.primaryText }, defaults.primaryText),
                secondaryText = color({ it.secondaryText }, defaults.secondaryText),
                componentText = color({ it.componentText }, defaults.componentText),
                placeholderText = color({ it.placeholderText }, defaults.placeholderText),
                icon = icon,
                error = color({ it.error }, defaults.error),
                borderRadius = (appearance.borderRadius ?: 8f).dp,
                borderWidth = (appearance.borderWidth ?: 1f).dp,
                fontScale = appearance.fontScale ?: 1f,
                fontFamily = resolveFontFamily(appearance.fontFamily),
                primaryButtonFontFamily = resolveFontFamily(
                    appearance.primaryButton?.fontFamily ?: appearance.fontFamily,
                ),
                primaryButtonBackground = parseColor(pbMode?.background ?: pbShared?.background)
                    ?: primary,
                primaryButtonText = parseColor(pbMode?.text ?: pbShared?.text) ?: Color.White,
                primaryButtonBorder = parseColor(pbMode?.border ?: pbShared?.border)
                    ?: primary,
                primaryButtonBorderRadius = (pbShapes?.borderRadius ?: DefaultPrimaryButtonRadius).dp,
                primaryButtonBorderWidth = (pbShapes?.borderWidth ?: 0f).dp,
                selectedBackground = primary.copy(alpha = if (isDark) 0x2E / 255f else 0x0F / 255f),
                accentIconBackground = primary.copy(alpha = if (isDark) 0x3D / 255f else 0x1F / 255f),
                containerBorder = optionalColor({ it.containerBorder }, inkAlpha(0x17)),
                neutralChip = inkAlpha(0x0D),
                fieldBorder = color({ it.componentBorder }, inkAlpha(0x1A)),
                fieldDivider = color({ it.componentDivider }, inkAlpha(0x14)),
                mutedIcon = if (isDark) icon else inkAlpha(0x52),
                unselectedRing = if (isDark) chromeAlpha(0x33) else inkAlpha(0x29),
                errorBorder = color({ it.error }, DefaultErrorBorder),
                errorText = color({ it.error }, DefaultErrorText),
                footerBorder = chromeAlpha(if (isDark) 0x14 else 0x0F),
                sheetHandle = chromeAlpha(if (isDark) 0x29 else 0x1F),
                orDivider = chromeAlpha(0x1A),
                visaMarkTint = if (isDark) Color.White else null,
                paymentContainerRadius = (appearance.borderRadius ?: DefaultPaymentContainerRadius).dp,
                formFieldRadius = (appearance.borderRadius ?: DefaultFormFieldRadius).dp,
                formFieldHeight = 54.dp,
            )
        }

        internal fun parseColorOrNone(raw: String?): Color? {
            if (raw == null) return null
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            when (trimmed.lowercase()) {
                "none", "transparent" -> return Color.Transparent
            }
            return parseColor(trimmed)
        }

        internal fun parseColor(hex: String?): Color? {
            val cleaned = hex?.trim()?.removePrefix("#") ?: return null
            if (cleaned.length == 8) {
                val value = cleaned.toLongOrNull(16) ?: return null
                return Color(
                    alpha = ((value shr 24) and 0xFF) / 255f,
                    red = ((value shr 16) and 0xFF) / 255f,
                    green = ((value shr 8) and 0xFF) / 255f,
                    blue = (value and 0xFF) / 255f,
                )
            }
            if (cleaned.length != 6) return null
            val value = cleaned.toLongOrNull(16) ?: return null
            return Color(0xFF000000 or value)
        }

        internal fun resolveFontFamily(name: String?): FontFamily {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) return PaymentFontFamily.family
            return FontFamily(typeface = Typeface.create(trimmed, Typeface.NORMAL))
        }

        private data class Palette(
            val primary: Color,
            val background: Color,
            val componentBackground: Color,
            val componentBorder: Color,
            val componentDivider: Color,
            val primaryText: Color,
            val secondaryText: Color,
            val componentText: Color,
            val placeholderText: Color,
            val icon: Color,
            val error: Color,
        )

        private object DefaultColors {
            val light = Palette(
                primary = Color(0xFF7C4DFF),
                background = Color(0xFFFFFFFF),
                componentBackground = Color(0xFFFFFFFF),
                componentBorder = Color(0xFFD1CDDB),
                componentDivider = Color(0xFFD1CDDB),
                primaryText = Color(0xFF16141A),
                secondaryText = Color(0xFF4A4653),
                componentText = Color(0xFF4A4653),
                placeholderText = Color(0xFF797585),
                icon = Color(0xFF797585),
                error = Color(0xFFFF6B6B),
            )
            val dark = Palette(
                primary = Color(0xFF7C4DFF),
                background = Color(0xFF18181B),
                componentBackground = Color(0xFF18181B),
                componentBorder = Color(0xFF3F3B48),
                componentDivider = Color(0xFF3F3B48),
                primaryText = Color(0xFFF7F6F9),
                secondaryText = Color(0xFFD1CDDB),
                componentText = Color(0xFFD1CDDB),
                placeholderText = Color(0xFF797585),
                icon = Color(0xFF797585),
                error = Color(0xFFFF6B6B),
            )
        }
    }
}
