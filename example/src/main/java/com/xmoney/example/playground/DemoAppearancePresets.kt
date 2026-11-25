package com.xmoney.example.playground

import com.xmoney.payments.config.Appearance
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
    val yellow = DemoAppearancePreset(
        "yellow",
        "Yellow",
        "Yellow brand in both modes",
        Appearance(brandColor = pair("#FACC15", "#FACC15")),
    )
    val black = DemoAppearancePreset(
        "black",
        "Black",
        "Black on a light sheet, white on the lifted black sheet",
        Appearance(
            brandColor = pair("#111111", "#FFFFFF"),
            backgroundColor = pair("#F4F4F5", "#000000"),
            neutralTint = pair("#6B7280", "#7B909A"),
        ),
    )
    val warmCream = DemoAppearancePreset(
        "warm_cream",
        "Warm cream",
        "Terracotta on cream, warm brown in dark",
        Appearance(
            brandColor = pair("#B4532A", "#F0B090"),
            backgroundColor = pair("#FBF5EA", "#140E0A"),
            neutralTint = pair("#8A7560", "#CDB59A"),
        ),
    )

    val all = listOf(xmoney, sharpBlue, roundedTeal, yellow, black, warmCream)
    val default = xmoney
}
