package com.xmoney.paymentelement.ui

import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.UserInterfaceStyle

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
object UIHelpers {
    fun isDarkMode(config: ResolvedPaymentConfig, isSystemDark: Boolean): Boolean =
        when (config.options.style) {
            UserInterfaceStyle.ALWAYS_LIGHT -> false
            UserInterfaceStyle.ALWAYS_DARK -> true
            else -> isSystemDark
        }
}