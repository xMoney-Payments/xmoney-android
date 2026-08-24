package com.xmoney.example

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.xmoney.example.theme.ExampleColors
import com.xmoney.example.theme.ExampleThemeController
import com.xmoney.payments.config.AppearanceColors
import com.xmoney.payments.config.AppearanceConfig
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.PrimaryButtonColors
import com.xmoney.payments.config.PrimaryButtonConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.config.UserInterfaceStyle
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.config.WalletButtonColor
import com.xmoney.payments.model.PaymentResult
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Default demo order: €19.99 (or [BuildConfig.CURRENCY]). */
internal const val SAMPLE_AMOUNT_MINOR = 1_999L

@Composable
internal fun exampleForcedStyle(): UserInterfaceStyle {
    val dark = ExampleThemeController.isDark(isSystemInDarkTheme())
    return if (dark) UserInterfaceStyle.ALWAYS_DARK else UserInterfaceStyle.ALWAYS_LIGHT
}

@Composable
internal fun exampleWalletAppearance(): WalletAppearance {
    val dark = ExampleThemeController.isDark(isSystemInDarkTheme())
    return WalletAppearance(
        color = if (dark) WalletButtonColor.WHITE else WalletButtonColor.BLACK,
    )
}

/**
 * Example-app [AppearanceConfig] so Sheet / Element / Google Pay match this
 * merchant chrome (especially dark). Copy this pattern in your app — SDK
 * defaults stay xMoney purple on a white card until you set `options.appearance`.
 *
 * [primary] is the interactive accent (Edit, selected marks, “Use other card”).
 * For a light brand fill, pass a dark readable [primary] and the fill as
 * [buttonBackground] so links stay above WCAG contrast on white.
 */
internal fun exampleAppearance(
    primary: Color = ExampleColors.Purple,
    primaryDark: Color = primary,
    buttonBackground: Color = primary,
    buttonText: Color = Color.White,
): AppearanceConfig {
    val pay = PrimaryButtonColors(
        background = buttonBackground.toAppearanceHex(),
        text = buttonText.toAppearanceHex(),
    )
    return AppearanceConfig(
        colorsLight = exampleAppearanceColors(
            primary = primary,
            background = ExampleColors.LightBg,
            component = ExampleColors.LightCard,
            text = ExampleColors.LightText,
            muted = ExampleColors.LightMuted,
            hairline = ExampleColors.LightHairline,
        ),
        colorsDark = exampleAppearanceColors(
            primary = primaryDark,
            background = ExampleColors.DarkBg,
            component = ExampleColors.DarkCard,
            text = ExampleColors.DarkText,
            muted = ExampleColors.DarkMuted,
            hairline = ExampleColors.DarkHairline,
        ),
        borderRadius = 24f,
        primaryButton = PrimaryButtonConfig(
            colorsLight = pay,
            colorsDark = pay,
            borderRadius = 9999f,
            borderWidth = 0f,
        ),
    )
}

@Composable
internal fun defaultPaymentConfig(
    googlePayEnabled: Boolean = true,
    savedCardsEnabled: Boolean = true,
    appearance: AppearanceConfig = exampleAppearance(),
): PaymentConfig {
    val style = exampleForcedStyle()
    val wallet = exampleWalletAppearance()
    return remember(style, wallet.color, appearance, googlePayEnabled, savedCardsEnabled) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(
                    enabled = googlePayEnabled,
                    appearance = wallet,
                ),
            ),
            card = CardConfig(
                savedCards = SavedCardsConfig(enabled = savedCardsEnabled),
            ),
            options = OptionsConfig(
                style = style,
                appearance = appearance,
            ),
        )
    }
}

internal fun formatMoney(amountMinor: Long, currency: String): String {
    val amount = BigDecimal.valueOf(amountMinor).movePointLeft(2)
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { format.currency = Currency.getInstance(currency.uppercase()) }
    val hasCents = amountMinor % 100L != 0L
    format.minimumFractionDigits = if (hasCents) 2 else 0
    format.maximumFractionDigits = 2
    return format.format(amount)
}

/** Complete/Failed always consume. Canceled consumes only after pay started (e.g. 3DS). */
internal fun orderConsumed(result: PaymentResult, didProcess: Boolean): Boolean =
    when (result) {
        is PaymentResult.Complete, is PaymentResult.Failed -> true
        PaymentResult.Canceled -> didProcess
    }

private fun exampleAppearanceColors(
    primary: Color,
    background: Color,
    component: Color,
    text: Color,
    muted: Color,
    hairline: Color,
): AppearanceColors {
    return AppearanceColors(
        primary = primary.toAppearanceHex(),
        background = background.toAppearanceHex(),
        componentBackground = component.toAppearanceHex(),
        componentBorder = hairline.toAppearanceHex(),
        componentDivider = hairline.toAppearanceHex(),
        primaryText = text.toAppearanceHex(),
        secondaryText = muted.toAppearanceHex(),
        componentText = text.toAppearanceHex(),
        placeholderText = muted.toAppearanceHex(),
        icon = muted.toAppearanceHex(),
        error = ExampleColors.Error.toAppearanceHex(),
        containerBorder = hairline.toAppearanceHex(),
    )
}

internal fun Color.toAppearanceHex(): String {
    val argb = toArgb()
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return if (a == 0xFF) {
        String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    } else {
        String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
    }
}
