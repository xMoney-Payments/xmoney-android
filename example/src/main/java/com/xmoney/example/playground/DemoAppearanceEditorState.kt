package com.xmoney.example.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xmoney.payments.config.Appearance
import com.xmoney.payments.config.ColorSchemePreference
import com.xmoney.payments.config.Corners
import com.xmoney.payments.config.ThemeColor

class DemoAppearanceEditorState {
    var selectedPresetId by mutableStateOf(DemoAppearancePresets.default.id)
    var appearance by mutableStateOf(DemoAppearancePresets.default.appearance)
    var style by mutableStateOf(ColorSchemePreference.AUTOMATIC.value)

    val appearanceSignature: String
        get() = listOf(
            selectedPresetId,
            style,
            appearance.typography.fontFamily.orEmpty(),
            appearance.brandColor?.light?.toString().orEmpty(),
            appearance.backgroundColor?.light?.toString().orEmpty(),
            appearance.neutralTint?.light?.toString().orEmpty(),
            appearance.accentColor?.light?.toString().orEmpty(),
            appearance.shape.corners.scale.toString(),
            appearance.shape.pillButtons.toString(),
        ).joinToString("|")

    fun applyPreset(preset: DemoAppearancePreset) {
        selectedPresetId = preset.id
        appearance = preset.appearance
    }

    fun updateStyle(value: String) {
        style = value
    }

    fun updateBrand(raw: String) {
        if (raw.isBlank()) {
            appearance = appearance.copy(brandColor = null)
            return
        }
        val hex = normalizeDemoHex(raw) ?: return
        val color = ThemeColor.of(hex) ?: return
        appearance = appearance.copy(brandColor = color)
    }

    fun updateCorners(corners: Corners) {
        appearance = appearance.copy(shape = appearance.shape.copy(corners = corners))
    }

    fun updatePillButtons(enabled: Boolean) {
        appearance = appearance.copy(shape = appearance.shape.copy(pillButtons = enabled))
    }

    fun updateFont(family: String) {
        appearance = appearance.copy(typography = appearance.typography.copy(fontFamily = family.ifBlank { null }))
    }

    fun toAppearance(): Appearance =
        appearance.copy(colorScheme = ColorSchemePreference.from(style))

    fun copyFrom(other: DemoAppearanceEditorState) {
        selectedPresetId = other.selectedPresetId
        appearance = other.appearance
        style = other.style
    }

    fun snapshot(): DemoAppearanceEditorState = DemoAppearanceEditorState().also {
        it.selectedPresetId = selectedPresetId
        it.appearance = appearance
        it.style = style
    }
}

internal fun normalizeDemoHex(raw: String): String? {
    var value = raw.trim().removePrefix("#").uppercase()
    val hex = value.all { it in '0'..'9' || it in 'A'..'F' }
    if (!hex) return null
    if (value.length == 3) {
        value = value.map { "$it$it" }.joinToString("")
    }
    if (value.length != 6 && value.length != 8) return null
    return "#$value"
}

internal fun demoBrandHex(appearance: Appearance): String {
    val argb = appearance.brandColor?.light ?: return ""
    return String.format("#%06X", argb and 0xFFFFFF)
}
