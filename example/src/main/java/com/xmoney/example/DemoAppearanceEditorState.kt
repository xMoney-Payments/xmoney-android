package com.xmoney.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xmoney.payments.config.AppearanceColors
import com.xmoney.payments.config.AppearanceConfig
import com.xmoney.payments.config.PrimaryButtonColors
import com.xmoney.payments.config.PrimaryButtonConfig

class DemoAppearanceEditorState(
    initial: DemoAppearancePreset = DemoAppearancePresets.default,
) {
    var selectedPresetId by mutableStateOf<String?>(initial.id)
        private set

    var style by mutableStateOf(initial.style)
        private set

    var primary by mutableStateOf(initial.colorOr("#7C4DFF") { it.primary })
    var background by mutableStateOf(initial.colorOr("#FFFFFF") { it.background })
    var componentBackground by mutableStateOf(initial.colorOr("#FFFFFF") { it.componentBackground })
    var componentBorder by mutableStateOf(initial.colorOr("#D1CDDB") { it.componentBorder })
    var containerBorder by mutableStateOf(
        initial.appearance.colors?.containerBorder?.normalizeAppearanceColor()
            ?: DEFAULT_CONTAINER_BORDER,
    )
    var primaryText by mutableStateOf(initial.colorOr("#16141A") { it.primaryText })
    var secondaryText by mutableStateOf(initial.colorOr("#4A4653") { it.secondaryText })
    var error by mutableStateOf(initial.colorOr("#FF6B6B") { it.error })

    var primaryButtonBackground by mutableStateOf(
        initial.buttonColorOr(initial.colorOr("#7C4DFF") { it.primary }) { it.background },
    )
    var primaryButtonText by mutableStateOf(initial.buttonColorOr("#FFFFFF") { it.text })

    var borderRadius by mutableFloatStateOf(initial.appearance.borderRadius ?: 8f)
    var borderWidth by mutableFloatStateOf(initial.appearance.borderWidth ?: 1f)
    var fontScale by mutableFloatStateOf(initial.appearance.fontScale ?: 1f)
    var primaryButtonBorderRadius by mutableFloatStateOf(
        initial.appearance.primaryButton?.borderRadius ?: 12f,
    )

    val appearanceSignature: String
        get() = listOf(
            style,
            primary,
            background,
            componentBackground,
            componentBorder,
            containerBorder,
            primaryText,
            secondaryText,
            error,
            primaryButtonBackground,
            primaryButtonText,
            borderRadius,
            borderWidth,
            fontScale,
            primaryButtonBorderRadius,
        ).joinToString("|")

    fun applyPreset(preset: DemoAppearancePreset) {
        selectedPresetId = preset.id
        style = preset.style
        primary = preset.colorOr("#7C4DFF") { it.primary }
        background = preset.colorOr("#FFFFFF") { it.background }
        componentBackground = preset.colorOr("#FFFFFF") { it.componentBackground }
        componentBorder = preset.colorOr("#D1CDDB") { it.componentBorder }
        containerBorder = preset.appearance.colors?.containerBorder?.normalizeAppearanceColor()
            ?: DEFAULT_CONTAINER_BORDER
        primaryText = preset.colorOr("#16141A") { it.primaryText }
        secondaryText = preset.colorOr("#4A4653") { it.secondaryText }
        error = preset.colorOr("#FF6B6B") { it.error }
        primaryButtonBackground = preset.buttonColorOr(primary) { it.background }
        primaryButtonText = preset.buttonColorOr("#FFFFFF") { it.text }
        borderRadius = preset.appearance.borderRadius ?: 8f
        borderWidth = preset.appearance.borderWidth ?: 1f
        fontScale = preset.appearance.fontScale ?: 1f
        primaryButtonBorderRadius = preset.appearance.primaryButton?.borderRadius ?: 12f
    }

    fun updateStyle(value: String) {
        style = value
        markCustomized()
    }

    fun markCustomized() {
        selectedPresetId = null
    }

    fun copyFrom(other: DemoAppearanceEditorState) {
        selectedPresetId = other.selectedPresetId
        style = other.style
        primary = other.primary
        background = other.background
        componentBackground = other.componentBackground
        componentBorder = other.componentBorder
        containerBorder = other.containerBorder
        primaryText = other.primaryText
        secondaryText = other.secondaryText
        error = other.error
        primaryButtonBackground = other.primaryButtonBackground
        primaryButtonText = other.primaryButtonText
        borderRadius = other.borderRadius
        borderWidth = other.borderWidth
        fontScale = other.fontScale
        primaryButtonBorderRadius = other.primaryButtonBorderRadius
    }

    fun snapshot(): DemoAppearanceEditorState {
        val copy = DemoAppearanceEditorState(DemoAppearancePresets.default)
        copy.copyFrom(this)
        return copy
    }

    fun toAppearanceConfig(): AppearanceConfig {
        if (selectedPresetId == DemoAppearancePresets.default.id) {
            return AppearanceConfig()
        }
        return AppearanceConfig(
            colors = AppearanceColors(
                primary = primary.normalizeHex(),
                background = background.normalizeHex(),
                componentBackground = componentBackground.normalizeHex(),
                componentBorder = componentBorder.normalizeHex(),
                componentDivider = componentBorder.normalizeHex(),
                primaryText = primaryText.normalizeHex(),
                secondaryText = secondaryText.normalizeHex(),
                componentText = primaryText.normalizeHex(),
                placeholderText = secondaryText.normalizeHex(),
                icon = secondaryText.normalizeHex(),
                error = error.normalizeHex(),
                containerBorder = containerBorder.normalizeAppearanceColor(),
            ),
            borderRadius = borderRadius,
            borderWidth = borderWidth,
            fontScale = fontScale,
            primaryButton = PrimaryButtonConfig(
                colors = PrimaryButtonColors(
                    background = primaryButtonBackground.normalizeHex(),
                    text = primaryButtonText.normalizeHex(),
                ),
                borderRadius = primaryButtonBorderRadius,
                borderWidth = 0f,
            ),
        )
    }

    companion object {
        const val DEFAULT_CONTAINER_BORDER = "#1716141A"

        private fun DemoAppearancePreset.colorOr(
            fallback: String,
            selector: (AppearanceColors) -> String?,
        ): String {
            val colors = appearance.colors ?: return fallback
            return selector(colors)?.normalizeAppearanceColor() ?: fallback
        }

        private fun DemoAppearancePreset.buttonColorOr(
            fallback: String,
            selector: (PrimaryButtonColors) -> String?,
        ): String {
            val colors = appearance.primaryButton?.colors ?: return fallback
            return selector(colors)?.normalizeHex() ?: fallback
        }

        fun String.normalizeHex(): String {
            val cleaned = trim().removePrefix("#").uppercase()
            return when (cleaned.length) {
                6, 8 -> "#$cleaned"
                else -> this.trim().let { if (it.startsWith("#")) it else "#$it" }
            }
        }

        fun String.normalizeAppearanceColor(): String {
            val trimmed = trim()
            if (trimmed.equals("none", ignoreCase = true) ||
                trimmed.equals("transparent", ignoreCase = true)
            ) {
                return "none"
            }
            return normalizeHex()
        }
    }
}

fun parseDemoHexColor(hex: String): androidx.compose.ui.graphics.Color? {
    val trimmed = hex.trim()
    if (trimmed.equals("none", ignoreCase = true) ||
        trimmed.equals("transparent", ignoreCase = true)
    ) {
        return androidx.compose.ui.graphics.Color.Transparent
    }
    val cleaned = trimmed.removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> androidx.compose.ui.graphics.Color(("FF$cleaned").toLong(16))
            8 -> androidx.compose.ui.graphics.Color(cleaned.toLong(16))
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}
