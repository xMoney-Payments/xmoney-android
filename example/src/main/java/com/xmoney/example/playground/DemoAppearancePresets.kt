package com.xmoney.example.playground

import com.xmoney.payments.config.Appearance
import com.xmoney.payments.config.ColorSchemePreference
import com.xmoney.payments.config.Corners
import com.xmoney.payments.config.Shape
import com.xmoney.payments.config.ThemeColor

data class DemoAppearancePreset(
    val id: String,
    val label: String,
    val description: String,
    val appearance: Appearance,
)

private fun pair(light: String, dark: String) = ThemeColor.of(light, dark)!!

internal object DemoAppearancePresets {
    val xmoney = DemoAppearancePreset("default", "xMoney", "Built-in light and dark", Appearance())
    val sharpBlue = DemoAppearancePreset(
        "sharp_blue",
        "Sharp blue",
        "Square corners, blue brand in both modes",
        Appearance(brandColor = pair("#1D4ED8", "#436CDF"), shape = Shape(Corners.Sharp, pillButtons = false)),
    )
    val roundedTeal = DemoAppearancePreset(
        "rounded_teal",
        "Rounded teal",
        "Rounded corners, teal brand in both modes",
        Appearance(brandColor = pair("#0F766E", "#0F766E"), shape = Shape(corners = Corners.Rounded)),
    )
    val blackBrand = DemoAppearancePreset(
        "black_brand",
        "Black brand",
        "Near-black brand, light scheme",
        Appearance(colorScheme = ColorSchemePreference.LIGHT, brandColor = pair("#16141A", "#16141A")),
    )
    val yellow = DemoAppearancePreset(
        "yellow",
        "Yellow",
        "Yellow brand in both modes",
        Appearance(brandColor = pair("#FACC15", "#FACC15")),
    )
    val xportal = DemoAppearancePreset(
        "xportal",
        "xPortal",
        "White brand on the lifted sheet",
        Appearance(
            colorScheme = ColorSchemePreference.DARK,
            brandColor = pair("#FFFFFF", "#FFFFFF"),
            backgroundColor = pair("#000000", "#000000"),
            neutralTint = pair("#7B909A", "#7B909A"),
        ),
    )
    val warmCream = DemoAppearancePreset(
        "warm_cream",
        "Warm cream",
        "Terracotta on cream",
        Appearance(
            colorScheme = ColorSchemePreference.LIGHT,
            brandColor = pair("#B4532A", "#B4532A"),
            backgroundColor = pair("#FBF5EA", "#FBF5EA"),
            neutralTint = pair("#8A7560", "#8A7560"),
        ),
    )

    val all = listOf(xmoney, sharpBlue, roundedTeal, blackBrand, yellow, xportal, warmCream)
    val default = xmoney
}
