package com.xmoney.example

import com.xmoney.payments.config.AppearanceColors
import com.xmoney.payments.config.AppearanceConfig
import com.xmoney.payments.config.PrimaryButtonColors
import com.xmoney.payments.config.PrimaryButtonConfig

data class DemoAppearancePreset(
    val id: String,
    val label: String,
    val description: String,
    val style: String,
    val appearance: AppearanceConfig,
    val swatches: List<String>,
)

object DemoAppearancePresets {
    val default = DemoAppearancePreset(
        id = "default",
        label = "Default",
        description = "SDK defaults",
        style = "automatic",
        appearance = AppearanceConfig(),
        swatches = listOf("#7C4DFF", "#FFFFFF", "#16141A", "#D1CDDB"),
    )

    val night = DemoAppearancePreset(
        id = "night",
        label = "Night",
        description = "Dark surfaces, indigo accent",
        style = "alwaysDark",
        appearance = appearance(
            colors = colors(
                primary = "#818CF8",
                background = "#0B0B0F",
                componentBackground = "#16161D",
                componentBorder = "#2E2E3A",
                componentDivider = "#2E2E3A",
                primaryText = "#F4F4F8",
                secondaryText = "#A8A8B8",
                componentText = "#E8E8F0",
                placeholderText = "#6E6E80",
                icon = "#A8A8B8",
                error = "#F87171",
            ),
            borderRadius = 12f,
            borderWidth = 1f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#6366F1",
                text = "#FFFFFF",
                borderRadius = 12f,
            ),
        ),
        swatches = listOf("#6366F1", "#0B0B0F", "#F4F4F8", "#2E2E3A"),
    )

    val softLight = DemoAppearancePreset(
        id = "soft_light",
        label = "Soft light",
        description = "Warm paper, soft teal, no container border",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#0E7C66",
                background = "#F7F3EC",
                componentBackground = "#FFFBF5",
                componentBorder = "#D9D0C3",
                componentDivider = "#E8DFD2",
                primaryText = "#14202B",
                secondaryText = "#5C6B78",
                componentText = "#14202B",
                placeholderText = "#8A96A1",
                icon = "#5C6B78",
                error = "#B42318",
                containerBorder = "none",
            ),
            borderRadius = 14f,
            borderWidth = 1f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#0E7C66",
                text = "#FFFFFF",
                borderRadius = 14f,
            ),
        ),
        swatches = listOf("#0E7C66", "#F7F3EC", "#14202B", "#D9D0C3"),
    )

    val minimalSharp = DemoAppearancePreset(
        id = "minimal_sharp",
        label = "Minimal sharp",
        description = "High contrast, square corners",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#111111",
                background = "#FFFFFF",
                componentBackground = "#FFFFFF",
                componentBorder = "#111111",
                componentDivider = "#E5E5E5",
                primaryText = "#111111",
                secondaryText = "#555555",
                componentText = "#111111",
                placeholderText = "#888888",
                icon = "#111111",
                error = "#D92D20",
            ),
            borderRadius = 0f,
            borderWidth = 1.5f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#111111",
                text = "#FFFFFF",
                borderRadius = 0f,
                borderWidth = 0f,
            ),
        ),
        swatches = listOf("#111111", "#FFFFFF", "#555555", "#E5E5E5"),
    )

    val roundedFriendly = DemoAppearancePreset(
        id = "rounded_friendly",
        label = "Rounded friendly",
        description = "Large radii, pastel surfaces",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#7C5CFC",
                background = "#F5F3FF",
                componentBackground = "#FFFFFF",
                componentBorder = "#E4DEFF",
                componentDivider = "#EDE9FE",
                primaryText = "#2E1064",
                secondaryText = "#6B7280",
                componentText = "#2E1064",
                placeholderText = "#9CA3AF",
                icon = "#7C5CFC",
                error = "#EF4444",
            ),
            borderRadius = 22f,
            borderWidth = 1f,
            fontScale = 1.02f,
            primaryButton = primaryButton(
                background = "#7C5CFC",
                text = "#FFFFFF",
                borderRadius = 28f,
            ),
        ),
        swatches = listOf("#7C5CFC", "#F5F3FF", "#2E1064", "#E4DEFF"),
    )

    val forest = DemoAppearancePreset(
        id = "forest",
        label = "Forest",
        description = "Demo teal and navy",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#0E7C66",
                background = "#F3EDE3",
                componentBackground = "#FFFBF5",
                componentBorder = "#D9D0C3",
                componentDivider = "#E8DFD2",
                primaryText = "#0B1F33",
                secondaryText = "#5C6B78",
                componentText = "#0B1F33",
                placeholderText = "#8A96A1",
                icon = "#0B1F33",
                error = "#B42318",
            ),
            borderRadius = 16f,
            borderWidth = 1f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#0E7C66",
                text = "#FFFFFF",
                borderRadius = 16f,
            ),
        ),
        swatches = listOf("#0E7C66", "#0B1F33", "#F3EDE3", "#D9D0C3"),
    )

    val ocean = DemoAppearancePreset(
        id = "ocean",
        label = "Ocean",
        description = "Deep blue, cool gray",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#0B5FFF",
                background = "#F4F7FB",
                componentBackground = "#FFFFFF",
                componentBorder = "#C9D4E3",
                componentDivider = "#E2E8F0",
                primaryText = "#0F172A",
                secondaryText = "#64748B",
                componentText = "#0F172A",
                placeholderText = "#94A3B8",
                icon = "#475569",
                error = "#DC2626",
            ),
            borderRadius = 12f,
            borderWidth = 1f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#0B5FFF",
                text = "#FFFFFF",
                borderRadius = 12f,
            ),
        ),
        swatches = listOf("#0B5FFF", "#F4F7FB", "#0F172A", "#C9D4E3"),
    )

    val sunset = DemoAppearancePreset(
        id = "sunset",
        label = "Sunset",
        description = "Warm coral on cream",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#E85D4C",
                background = "#FFF8F1",
                componentBackground = "#FFFFFF",
                componentBorder = "#F0D9C8",
                componentDivider = "#F5E6DA",
                primaryText = "#3B1F14",
                secondaryText = "#8B6B5C",
                componentText = "#3B1F14",
                placeholderText = "#B08978",
                icon = "#C2410C",
                error = "#B91C1C",
            ),
            borderRadius = 16f,
            borderWidth = 1f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#E85D4C",
                text = "#FFFFFF",
                borderRadius = 16f,
            ),
        ),
        swatches = listOf("#E85D4C", "#FFF8F1", "#3B1F14", "#F0D9C8"),
    )

    val contrast = DemoAppearancePreset(
        id = "contrast",
        label = "Contrast",
        description = "Bold black, stronger type",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#000000",
                background = "#FFFFFF",
                componentBackground = "#FAFAFA",
                componentBorder = "#000000",
                componentDivider = "#D4D4D4",
                primaryText = "#000000",
                secondaryText = "#404040",
                componentText = "#000000",
                placeholderText = "#737373",
                icon = "#000000",
                error = "#DC2626",
            ),
            borderRadius = 8f,
            borderWidth = 2f,
            fontScale = 1.12f,
            primaryButton = primaryButton(
                background = "#000000",
                text = "#FFFFFF",
                borderRadius = 8f,
                borderWidth = 2f,
                border = "#000000",
            ),
        ),
        swatches = listOf("#000000", "#FFFFFF", "#404040", "#D4D4D4"),
    )

    val slate = DemoAppearancePreset(
        id = "slate",
        label = "Slate",
        description = "Muted enterprise blue-gray",
        style = "alwaysLight",
        appearance = appearance(
            colors = colors(
                primary = "#334155",
                background = "#F8FAFC",
                componentBackground = "#FFFFFF",
                componentBorder = "#CBD5E1",
                componentDivider = "#E2E8F0",
                primaryText = "#0F172A",
                secondaryText = "#64748B",
                componentText = "#1E293B",
                placeholderText = "#94A3B8",
                icon = "#64748B",
                error = "#B91C1C",
            ),
            borderRadius = 8f,
            borderWidth = 1f,
            fontScale = 1f,
            primaryButton = primaryButton(
                background = "#334155",
                text = "#FFFFFF",
                borderRadius = 8f,
            ),
        ),
        swatches = listOf("#334155", "#F8FAFC", "#0F172A", "#CBD5E1"),
    )

    val all: List<DemoAppearancePreset> = listOf(
        default,
        night,
        softLight,
        minimalSharp,
        roundedFriendly,
        forest,
        ocean,
        sunset,
        contrast,
        slate,
    )

    fun byId(id: String?): DemoAppearancePreset? = all.firstOrNull { it.id == id }

    private fun colors(
        primary: String,
        background: String,
        componentBackground: String,
        componentBorder: String,
        componentDivider: String,
        primaryText: String,
        secondaryText: String,
        componentText: String,
        placeholderText: String,
        icon: String,
        error: String,
        containerBorder: String? = null,
    ) = AppearanceColors(
        primary = primary,
        background = background,
        componentBackground = componentBackground,
        componentBorder = componentBorder,
        componentDivider = componentDivider,
        primaryText = primaryText,
        secondaryText = secondaryText,
        componentText = componentText,
        placeholderText = placeholderText,
        icon = icon,
        error = error,
        containerBorder = containerBorder,
    )

    private fun primaryButton(
        background: String,
        text: String,
        borderRadius: Float,
        borderWidth: Float = 0f,
        border: String? = null,
    ) = PrimaryButtonConfig(
        colors = PrimaryButtonColors(
            background = background,
            text = text,
            border = border,
        ),
        borderRadius = borderRadius,
        borderWidth = borderWidth,
    )

    private fun appearance(
        colors: AppearanceColors,
        borderRadius: Float,
        borderWidth: Float,
        fontScale: Float,
        primaryButton: PrimaryButtonConfig,
    ) = AppearanceConfig(
        colors = colors,
        borderRadius = borderRadius,
        borderWidth = borderWidth,
        fontScale = fontScale,
        primaryButton = primaryButton,
    )
}
