package com.xmoney.paymentelement.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmoney.payments.config.AppearanceLog
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
    val brandTileBackground: Color,
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
    val primaryButtonPressed: Color = primaryButtonBackground,
    val primaryButtonDisabledBackground: Color = primaryButtonBackground,
    val primaryButtonDisabledText: Color = primaryButtonText,
    val secondaryButtonBackground: Color = primaryButtonBackground,
    val secondaryButtonText: Color = primaryButtonText,
    val focusRing: Color = primary,
    val stateIndicatorBorder: Color = primary,
    val checkboxRadius: Dp = 8.dp,
    val brandShadow: Color = primary.copy(alpha = 0.30f),
    val sheetCornerRadius: Dp = 32.dp,
    val rowRadius: Dp = 16.dp,
    val brandTileRadius: Dp = 12.dp,
    val sheetText: Color = primaryText,
    val isDarkSurface: Boolean = isDark,
    val orLabel: Color = metaText,
) {
    val brandChipBorder: Color
        get() = fieldBorder
    val text: Color get() = primaryText
    val fieldBackground: Color get() = componentBackground
    val border: Color get() = componentBorder
    val cornerRadius: Dp get() = borderRadius

    val containerBorderWidth: Dp
        get() = if (containerBorder.alpha == 0f) 0.dp else borderWidth

    /** Close and back icons sit on the sheet. Field text can follow a lighter card. */
    val sheetChromeText: Color
        get() = sheetText

    /** Corner radius for any button, following `primaryButton.borderRadius` so every button shares one shape. */
    fun buttonRadius(height: Dp): Dp = primaryButtonBorderRadius.coerceIn(0.dp, height / 2)

    /** Idle stroke follows `borderWidth`. Error and focus stay visible at 1.5 when the idle stroke is thinner. */
    fun fieldStrokeWidth(hasError: Boolean, focused: Boolean = false): Dp =
        if (hasError || focused) maxOf(borderWidth, 1.5.dp) else borderWidth

    /** Compose `.sp` already applies the system font scale. Do not multiply [fontScale] in. */
    fun scaledSp(size: Float): TextUnit = size.sp

    companion object {
        val BrandPrimary = Color(0xFF7C4DFF)
        const val DefaultPrimaryButtonRadius = 9999f
        const val DefaultFormFieldRadius = 16f
        const val DefaultPaymentContainerRadius = 24f
        const val DefaultRowRadius = 16f
        const val DefaultBrandTileRadius = 12f

        fun resolve(
            config: ResolvedPaymentConfig,
            isDark: Boolean,
            contentSizeScale: Float = 1f,
            increasedContrast: Boolean = false,
        ): CheckoutTheme = ThemeDerivation.resolve(
            config.options.appearance,
            isDark,
            contentSizeScale,
            increasedContrast,
        )

        internal fun resolveFontFamily(name: String?): FontFamily {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) return PaymentFontFamily.family
            val regular = Typeface.create(trimmed, Typeface.NORMAL)
            if (regular == Typeface.DEFAULT || regular == Typeface.SANS_SERIF) {
                AppearanceLog.warn("font family '$trimmed' is not registered")
                return PaymentFontFamily.family
            }
            val bold = Typeface.create(trimmed, Typeface.BOLD)
            return FontFamily(
                LoadedTypefaceFont(FontWeight.Normal, regular),
                LoadedTypefaceFont(FontWeight.Medium, bold),
                LoadedTypefaceFont(FontWeight.SemiBold, bold),
                LoadedTypefaceFont(FontWeight.Bold, bold),
            )
        }

        private class LoadedTypefaceFont(
            override val weight: FontWeight,
            private val face: Typeface,
        ) : AndroidFont(
            FontLoadingStrategy.Blocking,
            object : TypefaceLoader {
                override fun loadBlocking(context: Context, font: AndroidFont): Typeface = face
                override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = face
            },
        ) {
            override val style: FontStyle = FontStyle.Normal
        }

    }
}
