package com.xmoney.paymentelement.ui

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun rememberIncreasedContrast(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(UIHelpers.increasedContrast(context)) }
    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT < 34) {
            onDispose {}
        } else {
            val manager = context.getSystemService(UiModeManager::class.java)
            val listener = UiModeManager.ContrastChangeListener { contrast ->
                enabled = contrast > 0f
            }
            manager?.addContrastChangeListener(context.mainExecutor, listener)
            onDispose { manager?.removeContrastChangeListener(listener) }
        }
    }
    return enabled
}