package com.xmoney.paymentelement.ui

import android.content.Context
import android.os.Build
import com.xmoney.payments.config.ColorSchemePreference
import com.xmoney.payments.config.ResolvedPaymentConfig

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
object UIHelpers {
    fun isDarkMode(config: ResolvedPaymentConfig, isSystemDark: Boolean): Boolean =
        when (config.options.appearance.colorScheme) {
            ColorSchemePreference.LIGHT -> false
            ColorSchemePreference.DARK -> true
            else -> isSystemDark
        }

    fun contentSizeScale(context: Context): Float {
        val scale = context.resources.configuration.fontScale
        return if (scale < 1f) 1f else scale
    }

    fun increasedContrast(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return try {
            val manager = context.getSystemService(android.app.UiModeManager::class.java)
            manager.contrast > 0f
        } catch (_: Throwable) {
            false
        }
    }
}