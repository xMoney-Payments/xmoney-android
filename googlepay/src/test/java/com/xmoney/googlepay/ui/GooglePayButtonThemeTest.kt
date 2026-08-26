package com.xmoney.googlepay.ui

import com.google.pay.button.ButtonTheme
import com.google.pay.button.ButtonType
import com.xmoney.payments.config.WalletButtonColor
import com.xmoney.payments.config.WalletButtonType
import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePayButtonThemeTest {
    @Test
    fun defaultsToDarkOnLightBackground() {
        assertEquals(ButtonTheme.Dark, resolveButtonTheme(null, isDarkBackground = false))
    }

    @Test
    fun defaultsToLightOnDarkBackground() {
        assertEquals(ButtonTheme.Light, resolveButtonTheme(null, isDarkBackground = true))
    }

    @Test
    fun explicitColorOverridesSurface() {
        assertEquals(ButtonTheme.Light, resolveButtonTheme(WalletButtonColor.WHITE, isDarkBackground = false))
        assertEquals(ButtonTheme.Dark, resolveButtonTheme(WalletButtonColor.BLACK, isDarkBackground = true))
    }

    @Test
    fun resolvesButtonTypeFromAppearance() {
        assertEquals(ButtonType.Pay, resolveButtonType(null))
        assertEquals(ButtonType.Pay, resolveButtonType(WalletButtonType.PAY))
        assertEquals(ButtonType.Buy, resolveButtonType(WalletButtonType.BUY))
        assertEquals(ButtonType.Checkout, resolveButtonType(WalletButtonType.CHECKOUT))
        assertEquals(ButtonType.Plain, resolveButtonType(WalletButtonType.PLAIN))
    }

    @Test
    fun colorFromAcceptsAliases() {
        assertEquals(WalletButtonColor.WHITE, WalletButtonColor.from("light"))
        assertEquals(WalletButtonColor.BLACK, WalletButtonColor.from("DARK"))
    }
}
