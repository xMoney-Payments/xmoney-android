package com.xmoney.example.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xmoney.payments.config.AppearanceColors
import com.xmoney.payments.config.AppearanceConfig
import com.xmoney.payments.config.PrimaryButtonColors
import com.xmoney.payments.config.PrimaryButtonConfig

class AppearancePalette {
    var primary by mutableStateOf("")
    var background by mutableStateOf("")
    var componentBackground by mutableStateOf("")
    var componentBorder by mutableStateOf("")
    var componentDivider by mutableStateOf("")
    var containerBorder by mutableStateOf("")
    var primaryText by mutableStateOf("")
    var secondaryText by mutableStateOf("")
    var componentText by mutableStateOf("")
    var placeholderText by mutableStateOf("")
    var icon by mutableStateOf("")
    var error by mutableStateOf("")
    var primaryButtonBackground by mutableStateOf("")
    var primaryButtonText by mutableStateOf("")
    var primaryButtonBorder by mutableStateOf("")

    val isEmpty: Boolean
        get() = listOf(
            primary,
            background,
            componentBackground,
            componentBorder,
            componentDivider,
            containerBorder,
            primaryText,
            secondaryText,
            componentText,
            placeholderText,
            icon,
            error,
            primaryButtonBackground,
            primaryButtonText,
            primaryButtonBorder,
        ).all { it.isBlank() }

    val signature: String
        get() = listOf(
            primary,
            background,
            componentBackground,
            componentBorder,
            componentDivider,
            containerBorder,
            primaryText,
            secondaryText,
            componentText,
            placeholderText,
            icon,
            error,
            primaryButtonBackground,
            primaryButtonText,
            primaryButtonBorder,
        ).joinToString("|")

    fun clear() {
        primary = ""
        background = ""
        componentBackground = ""
        componentBorder = ""
        componentDivider = ""
        containerBorder = ""
        primaryText = ""
        secondaryText = ""
        componentText = ""
        placeholderText = ""
        icon = ""
        error = ""
        primaryButtonBackground = ""
        primaryButtonText = ""
        primaryButtonBorder = ""
    }

    fun copyFrom(other: AppearancePalette) {
        primary = other.primary
        background = other.background
        componentBackground = other.componentBackground
        componentBorder = other.componentBorder
        componentDivider = other.componentDivider
        containerBorder = other.containerBorder
        primaryText = other.primaryText
        secondaryText = other.secondaryText
        componentText = other.componentText
        placeholderText = other.placeholderText
        icon = other.icon
        error = other.error
        primaryButtonBackground = other.primaryButtonBackground
        primaryButtonText = other.primaryButtonText
        primaryButtonBorder = other.primaryButtonBorder
    }

    fun loadSharedDefaults() {
        primary = SharedDefaults.primary
        background = SharedDefaults.background
        componentBackground = SharedDefaults.componentBackground
        componentBorder = SharedDefaults.componentBorder
        componentDivider = SharedDefaults.componentDivider
        containerBorder = SharedDefaults.containerBorder
        primaryText = SharedDefaults.primaryText
        secondaryText = SharedDefaults.secondaryText
        componentText = SharedDefaults.componentText
        placeholderText = SharedDefaults.placeholderText
        icon = SharedDefaults.icon
        error = SharedDefaults.error
        primaryButtonBackground = SharedDefaults.primaryButtonBackground
        primaryButtonText = SharedDefaults.primaryButtonText
        primaryButtonBorder = SharedDefaults.primaryButtonBorder
    }

    fun loadFrom(appearance: AppearanceConfig) {
        val colors = appearance.colors
        val button = appearance.primaryButton?.colors
        primary = colors?.primary.orDefault(SharedDefaults.primary)
        background = colors?.background.orDefault(SharedDefaults.background)
        componentBackground = colors?.componentBackground.orDefault(SharedDefaults.componentBackground)
        componentBorder = colors?.componentBorder.orDefault(SharedDefaults.componentBorder)
        componentDivider = colors?.componentDivider.orDefault(
            colors?.componentBorder.orDefault(SharedDefaults.componentDivider),
        )
        containerBorder = colors?.containerBorder?.normalizeAppearanceColor()
            ?: SharedDefaults.containerBorder
        primaryText = colors?.primaryText.orDefault(SharedDefaults.primaryText)
        secondaryText = colors?.secondaryText.orDefault(SharedDefaults.secondaryText)
        componentText = colors?.componentText.orDefault(
            colors?.primaryText.orDefault(SharedDefaults.componentText),
        )
        placeholderText = colors?.placeholderText.orDefault(SharedDefaults.placeholderText)
        icon = colors?.icon.orDefault(SharedDefaults.icon)
        error = colors?.error.orDefault(SharedDefaults.error)
        val fallbackButtonFill = button?.background.orDefault(primary)
        primaryButtonBackground = fallbackButtonFill
        primaryButtonText = button?.text.orDefault(SharedDefaults.primaryButtonText)
        primaryButtonBorder = button?.border.orDefault(fallbackButtonFill)
    }

    fun toAppearanceColors(): AppearanceColors = AppearanceColors(
        primary = primary.normalizeHexOrNull(),
        background = background.normalizeHexOrNull(),
        componentBackground = componentBackground.normalizeHexOrNull(),
        componentBorder = componentBorder.normalizeHexOrNull(),
        componentDivider = componentDivider.normalizeHexOrNull(),
        primaryText = primaryText.normalizeHexOrNull(),
        secondaryText = secondaryText.normalizeHexOrNull(),
        componentText = componentText.normalizeHexOrNull(),
        placeholderText = placeholderText.normalizeHexOrNull(),
        icon = icon.normalizeHexOrNull(),
        error = error.normalizeHexOrNull(),
        containerBorder = containerBorder.normalizeAppearanceColorOrNull(),
    )

    fun toAppearanceColorsOrNull(): AppearanceColors? =
        if (isEmpty) null else toAppearanceColors()

    fun toButtonColors(): PrimaryButtonColors = PrimaryButtonColors(
        background = primaryButtonBackground.normalizeHexOrNull(),
        text = primaryButtonText.normalizeHexOrNull(),
        border = primaryButtonBorder.normalizeHexOrNull(),
    )

    fun toButtonColorsOrNull(): PrimaryButtonColors? {
        if (primaryButtonBackground.isBlank() &&
            primaryButtonText.isBlank() &&
            primaryButtonBorder.isBlank()
        ) {
            return null
        }
        return toButtonColors()
    }

    companion object {
        object SharedDefaults {
            const val primary = "#7C4DFF"
            const val background = "#FFFFFF"
            const val componentBackground = "#FFFFFF"
            const val componentBorder = "#D1CDDB"
            const val componentDivider = "#D1CDDB"
            const val containerBorder = "#1716141A"
            const val primaryText = "#16141A"
            const val secondaryText = "#4A4653"
            const val componentText = "#4A4653"
            const val placeholderText = "#797585"
            const val icon = "#797585"
            const val error = "#FF6B6B"
            const val primaryButtonBackground = "#7C4DFF"
            const val primaryButtonText = "#FFFFFF"
            const val primaryButtonBorder = "#7C4DFF"
        }

        private fun String?.orDefault(fallback: String): String {
            val value = this?.trim().orEmpty()
            if (value.isEmpty()) return fallback
            return value.normalizeAppearanceColor()
        }
    }
}

class DemoAppearanceEditorState(
    initial: DemoAppearancePreset = DemoAppearancePresets.default,
) {
    var selectedPresetId by mutableStateOf<String?>(initial.id)
        private set

    var style by mutableStateOf(initial.style)
        private set

    var paletteSlot by mutableStateOf(PaletteSlot.Shared)

    val shared = AppearancePalette()
    val light = AppearancePalette()
    val dark = AppearancePalette()

    var fontFamily by mutableStateOf(initial.appearance.fontFamily.orEmpty())
    var primaryButtonFontFamily by mutableStateOf(
        initial.appearance.primaryButton?.fontFamily.orEmpty(),
    )
    var borderRadius by mutableFloatStateOf(initial.appearance.borderRadius ?: 8f)
    var borderWidth by mutableFloatStateOf(initial.appearance.borderWidth ?: 1f)
    var fontScale by mutableFloatStateOf(initial.appearance.fontScale ?: 1f)
    var primaryButtonBorderRadius by mutableFloatStateOf(
        initial.appearance.primaryButton?.borderRadius ?: 9999f,
    )
    var primaryButtonBorderWidth by mutableFloatStateOf(
        initial.appearance.primaryButton?.borderWidth ?: 0f,
    )

    val currentPalette: AppearancePalette
        get() = when (paletteSlot) {
            PaletteSlot.Shared -> shared
            PaletteSlot.Light -> light
            PaletteSlot.Dark -> dark
        }

    val appearanceSignature: String
        get() = listOf(
            style,
            paletteSlot.name,
            shared.signature,
            light.signature,
            dark.signature,
            fontFamily,
            primaryButtonFontFamily,
            borderRadius,
            borderWidth,
            fontScale,
            primaryButtonBorderRadius,
            primaryButtonBorderWidth,
        ).joinToString("|")

    init {
        applyPreset(initial)
    }

    fun applyPreset(preset: DemoAppearancePreset) {
        selectedPresetId = preset.id
        style = preset.style
        paletteSlot = PaletteSlot.Shared
        if (preset.id == DemoAppearancePresets.default.id) {
            shared.loadSharedDefaults()
        } else {
            shared.loadFrom(preset.appearance)
        }
        light.clear()
        dark.clear()
        fontFamily = preset.appearance.fontFamily.orEmpty()
        primaryButtonFontFamily = preset.appearance.primaryButton?.fontFamily.orEmpty()
        borderRadius = preset.appearance.borderRadius ?: 8f
        borderWidth = preset.appearance.borderWidth ?: 1f
        fontScale = preset.appearance.fontScale ?: 1f
        primaryButtonBorderRadius = preset.appearance.primaryButton?.borderRadius ?: 9999f
        primaryButtonBorderWidth = preset.appearance.primaryButton?.borderWidth ?: 0f
    }

    fun updateStyle(value: String) {
        style = value
        paletteSlot = when (value) {
            "alwaysLight" -> PaletteSlot.Light
            "alwaysDark" -> PaletteSlot.Dark
            else -> PaletteSlot.Shared
        }
    }

    fun markCustomized() {
        selectedPresetId = null
    }

    fun editCurrentPalette(block: AppearancePalette.() -> Unit) {
        currentPalette.apply(block)
        markCustomized()
    }

    fun copyFrom(other: DemoAppearanceEditorState) {
        selectedPresetId = other.selectedPresetId
        style = other.style
        paletteSlot = other.paletteSlot
        shared.copyFrom(other.shared)
        light.copyFrom(other.light)
        dark.copyFrom(other.dark)
        fontFamily = other.fontFamily
        primaryButtonFontFamily = other.primaryButtonFontFamily
        borderRadius = other.borderRadius
        borderWidth = other.borderWidth
        fontScale = other.fontScale
        primaryButtonBorderRadius = other.primaryButtonBorderRadius
        primaryButtonBorderWidth = other.primaryButtonBorderWidth
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
            fontFamily = fontFamily.takeIf { it.isNotBlank() },
            fontScale = fontScale,
            colors = shared.toAppearanceColors(),
            colorsLight = light.toAppearanceColorsOrNull(),
            colorsDark = dark.toAppearanceColorsOrNull(),
            borderRadius = borderRadius,
            borderWidth = borderWidth,
            primaryButton = PrimaryButtonConfig(
                fontFamily = primaryButtonFontFamily.takeIf { it.isNotBlank() },
                colors = shared.toButtonColors(),
                colorsLight = light.toButtonColorsOrNull(),
                colorsDark = dark.toButtonColorsOrNull(),
                borderRadius = primaryButtonBorderRadius,
                borderWidth = primaryButtonBorderWidth,
            ),
        )
    }
}

private fun String.normalizeHexOrNull(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.normalizeHex()
}

private fun String.normalizeAppearanceColorOrNull(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.normalizeAppearanceColor()
}

fun parseDemoHexColor(hex: String): androidx.compose.ui.graphics.Color? {
    val trimmed = hex.trim()
    if (trimmed.isEmpty()) return null
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

internal fun String.normalizeHex(): String {
    val cleaned = trim().removePrefix("#").uppercase()
    return when (cleaned.length) {
        6, 8 -> "#$cleaned"
        else -> this.trim().let { if (it.startsWith("#")) it else "#$it" }
    }
}

internal fun String.normalizeAppearanceColor(): String {
    val trimmed = trim()
    if (trimmed.equals("none", ignoreCase = true) ||
        trimmed.equals("transparent", ignoreCase = true)
    ) {
        return "none"
    }
    return normalizeHex()
}
