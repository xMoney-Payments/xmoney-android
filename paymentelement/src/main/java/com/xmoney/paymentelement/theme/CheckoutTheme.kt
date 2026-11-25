package com.xmoney.paymentelement.theme

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
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
    val isDark: Boolean,
    val inputPlaceholder: Color,
    val metaText: Color,
    val sectionLabel: Color,
    val radioIdle: Color,
    val textBrand: Color,
    val iconBrand: Color,
    val errorBadge: Color,
    val dangerTint: Color,
    val dangerTintStrong: Color,
    val inverseText: Color,
    val poweredByText: Color,
    val poweredByLogo: Color,
) {
    val brandChipBorder: Color
        get() = fieldBorder
    val text: Color get() = primaryText
    val fieldBackground: Color get() = componentBackground
    val border: Color get() = componentBorder
    val cornerRadius: Dp get() = borderRadius

    val containerBorderWidth: Dp
        get() = if (containerBorder.alpha == 0f) 0.dp else 1.dp

    /** Corner radius for any button, following `primaryButton.borderRadius` so every button shares one shape. */
    fun buttonRadius(height: Dp): Dp = primaryButtonBorderRadius.coerceIn(0.dp, height / 2)

    fun fieldStrokeWidth(hasError: Boolean): Dp =
        if (hasError) maxOf(borderWidth, 1.5.dp) else borderWidth

    fun scaledSp(size: Float): TextUnit = (size * fontScale).sp

    companion object {
        val BrandPrimary = Color(0xFF7C4DFF)
        const val DefaultPrimaryButtonRadius = 9999f
        const val DefaultFormFieldRadius = 16f
        const val DefaultPaymentContainerRadius = 24f
        const val DefaultRowRadius = 16f
        const val DefaultBrandTileRadius = 12f
        /** `kyc-status-danger-solid`. The "!" badge keeps this in both themes. */
        private val DefaultErrorBadge = Color(0xFFDC2626)
        private val DefaultErrorTextLight = Color(0xFFDC2626)
        private val DefaultErrorTextDark = Color(0xFFEF4444)
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

            fun isSet(selector: (AppearanceColors) -> String?): Boolean =
                parseColor(modeColors?.let(selector) ?: sharedColors?.let(selector)) != null

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
            val primaryText = color({ it.primaryText }, defaults.primaryText)
            val primaryTextSet = isSet { it.primaryText }
            val icon = color({ it.icon }, defaults.icon)
            fun chromeAlpha(alpha: Int): Color =
                (if (isDark) DarkChrome else ink).copy(alpha = alpha / 255f)

            val secondaryText = color({ it.secondaryText }, defaults.secondaryText)
            val placeholderText = color({ it.placeholderText }, defaults.placeholderText)
            val unselectedRing = if (isDark) chromeAlpha(0x33) else inkAlpha(0x29)
            // Explicit kyc tokens until the merchant sets the source color. Dark no longer
            // pretends every surface is an alpha wash of primary.
            val derivesFromPrimary = isSet { it.primary }
            val derivesFromSecondary = isSet { it.secondaryText }
            val derivesFromPlaceholder = isSet { it.placeholderText }
            val textTertiary = if (isDark) Color(0xFF95959D) else Color(0xFF63606D)
            val textMuted = if (isDark) Color(0xFF85858D) else Color(0xFF6B6779)
            val placeholder = if (isDark) Color(0xFF69696F) else Color(0xFF9691A3)
            val poweredBy = if (isDark) Color(0xFF605C70) else Color(0xFF9691A3)
            val danger = if (isDark) DefaultErrorTextDark else DefaultErrorTextLight
            val borderDefault = if (isDark) chromeAlpha(0x1A) else Color(0xFFE3E1E8)
            val fieldBorderColor = color({ it.componentBorder }, borderDefault)
            val merchantError = isSet { it.error }

            return CheckoutTheme(
                primary = primary,
                background = color({ it.background }, defaults.background),
                componentBackground = color({ it.componentBackground }, defaults.componentBackground),
                componentBorder = color({ it.componentBorder }, defaults.componentBorder),
                componentDivider = color({ it.componentDivider }, defaults.componentDivider),
                primaryText = primaryText,
                secondaryText = secondaryText,
                componentText = color({ it.componentText }, defaults.componentText),
                placeholderText = placeholderText,
                icon = icon,
                error = color({ it.error }, defaults.error),
                borderRadius = (appearance.borderRadius ?: DefaultFormFieldRadius).dp,
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
                selectedBackground = if (derivesFromPrimary) {
                    primary.copy(alpha = (if (isDark) 0x2E else 0x0F) / 255f)
                } else {
                    if (isDark) Color(0xFF2A2A2F) else Color(0xFFF7F6FF)
                },
                accentIconBackground = if (derivesFromPrimary) {
                    primary.copy(alpha = (if (isDark) 0x3D else 0x1F) / 255f)
                } else {
                    if (isDark) Color(0xFF24124C) else Color(0xFFECE5FF)
                },
                containerBorder = optionalColor({ it.containerBorder }, if (isDark) inkAlpha(0x17) else Color.Transparent),
                neutralChip = inkAlpha(0x0D),
                fieldBorder = fieldBorderColor,
                fieldDivider = color({ it.componentDivider }, borderDefault),
                mutedIcon = when {
                    isSet { it.icon } -> icon
                    derivesFromPlaceholder -> placeholderText
                    else -> placeholder
                },
                unselectedRing = unselectedRing,
                errorBorder = if (merchantError) color({ it.error }, danger) else danger,
                errorText = if (merchantError) color({ it.error }, danger) else danger,
                footerBorder = chromeAlpha(if (isDark) 0x14 else 0x0F),
                sheetHandle = borderDefault,
                orDivider = borderDefault,
                visaMarkTint = if (isDark) Color.White else null,
                paymentContainerRadius = DefaultPaymentContainerRadius.dp,
                formFieldRadius = (appearance.borderRadius ?: DefaultFormFieldRadius).dp,
                formFieldHeight = 48.dp,
                isDark = isDark,
                inputPlaceholder = if (derivesFromPlaceholder) placeholderText else placeholder,
                metaText = if (derivesFromSecondary) secondaryText else textTertiary,
                sectionLabel = if (derivesFromSecondary) secondaryText else textMuted,
                radioIdle = if (derivesFromPrimary) {
                    unselectedRing
                } else if (isDark) {
                    Color(0xA1A180FF)
                } else {
                    Color(0xCC7C4DFF)
                },
                textBrand = if (derivesFromPrimary) primary else if (isDark) Color(0xFF926BFF) else Color(0xFF7A4BFB),
                iconBrand = if (derivesFromPrimary) primary else if (isDark) Color(0xFF926BFF) else Color(0xFF7C4DFF),
                errorBadge = if (merchantError) color({ it.error }, DefaultErrorBadge) else DefaultErrorBadge,
                dangerTint = Color(0xFFEF4444).copy(alpha = if (isDark) 0.15f else 0.08f),
                dangerTintStrong = Color(0xFFEF4444).copy(alpha = if (isDark) 0.30f else 0.09f),
                inverseText = if (primaryTextSet) {
                    if (primaryText.luminance() > 0.5f) LightInk else DarkInk
                } else if (isDark) {
                    Color(0xFF09090B)
                } else {
                    Color(0xFFF7F6FF)
                },
                poweredByText = if (derivesFromSecondary) secondaryText.copy(alpha = 0.30f) else poweredBy,
                poweredByLogo = if (derivesFromSecondary) secondaryText.copy(alpha = 0.42f) else poweredBy,
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
                background = Color(0xFFF7F6FF),
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
                componentBackground = Color(0xFF1F1F23),
                componentBorder = Color(0xFF3F3B48),
                componentDivider = Color(0xFF3F3B48),
                primaryText = Color(0xFFFAFAFA),
                secondaryText = Color(0xFFD1CDDB),
                componentText = Color(0xFFD1CDDB),
                placeholderText = Color(0xFF797585),
                icon = Color(0xFF797585),
                error = Color(0xFFFF6B6B),
            )
        }
    }
}
