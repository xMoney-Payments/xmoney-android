package com.xmoney.example.scenarios.merchant

import androidx.compose.ui.graphics.Color
import com.xmoney.example.R
import com.xmoney.example.exampleAppearance
import com.xmoney.example.theme.ExampleColors
import com.xmoney.payments.config.AppearanceConfig

internal enum class MerchantCatalogStyle { Menu, Plans, Grid }

internal enum class MerchantPaySurface { PaymentSheet, Embedded }

internal data class MerchantProduct(
    val id: String,
    val name: String,
    val category: String,
    val blurb: String,
    val priceMinor: Long,
    val imageRes: Int,
)

internal data class MerchantLine(
    val product: MerchantProduct,
    val quantity: Int,
) {
    val lineTotalMinor: Long get() = product.priceMinor * quantity
}

internal data class MerchantBrand(
    val name: String,
    val tagline: String,
    val emptyHint: String,
    val catalogStyle: MerchantCatalogStyle,
    val paySurface: MerchantPaySurface,
    val products: List<MerchantProduct>,
    val appearance: AppearanceConfig,
    val accent: Color,
    val onAccent: Color,
    /** Text-on-surface accent (prices, filled chips). Defaults to [accent]. */
    val accentText: Color = accent,
)

internal val List<MerchantLine>.itemCount: Int get() = sumOf { it.quantity }

internal val List<MerchantLine>.subtotalMinor: Long get() = sumOf { it.lineTotalMinor }

internal fun Map<String, Int>.toMerchantLines(products: List<MerchantProduct>): List<MerchantLine> =
    products.mapNotNull { product ->
        val qty = this[product.id] ?: return@mapNotNull null
        if (qty <= 0) null else MerchantLine(product, qty)
    }

private val HearthTerracotta = Color(0xFFC45C26)

internal val LumenBrand = MerchantBrand(
    name = "Lumen",
    tagline = "Modern essentials. Pay with Payment Sheet.",
    emptyHint = "Add a few Lumen pieces from the store, then pay with Payment Sheet.",
    catalogStyle = MerchantCatalogStyle.Grid,
    paySurface = MerchantPaySurface.PaymentSheet,
    appearance = exampleAppearance(),
    accent = ExampleColors.Purple,
    onAccent = Color.White,
    products = listOf(
        MerchantProduct("earbuds", "Aura Earbuds", "Audio", "Spatial audio, 32-hour case", 12_900, R.drawable.product_earbuds),
        MerchantProduct("lamp", "Arc Desk Lamp", "Lighting", "Dimmable, brushed aluminum", 8_900, R.drawable.product_lamp),
        MerchantProduct("pour-over", "Stone Pour-Over", "Kitchen", "Matte ceramic, 600 ml", 4_200, R.drawable.product_pourover),
        MerchantProduct("throw", "Merino Throw", "Home", "Undyed wool, 140 × 200", 7_500, R.drawable.product_throw),
        MerchantProduct("notebooks", "Oak Notebooks", "Stationery", "Set of three, linen cover", 2_400, R.drawable.product_notebooks),
        MerchantProduct("weekender", "Canvas Weekender", "Travel", "Vegetable-tanned straps", 11_800, R.drawable.product_weekender),
        MerchantProduct("diffuser", "Ceramic Diffuser", "Wellness", "Ultrasonic, 4-hour timer", 5_400, R.drawable.product_diffuser),
        MerchantProduct("bottle", "Steel Bottle", "Everyday", "Double-wall, 750 ml", 3_200, R.drawable.product_bottle),
    ),
)

internal val HearthBrand = MerchantBrand(
    name = "Hearth",
    tagline = "Neighbourhood café. Pay in-page with Embedded Element.",
    emptyHint = "Add a coffee or a plate from the board, then check out in this screen.",
    catalogStyle = MerchantCatalogStyle.Menu,
    paySurface = MerchantPaySurface.Embedded,
    appearance = exampleAppearance(primary = HearthTerracotta),
    accent = HearthTerracotta,
    onAccent = Color.White,
    products = listOf(
        MerchantProduct("espresso", "House Espresso", "Drinks", "Single origin, 18g", 350, R.drawable.product_espresso),
        MerchantProduct("cortado", "Oat Cortado", "Drinks", "Equal parts, steamed oat", 420, R.drawable.product_cortado),
        MerchantProduct("cold-brew", "Cold Brew", "Drinks", "16-hour steep, served over ice", 480, R.drawable.product_coldbrew),
        MerchantProduct("grain-bowl", "Seasonal Grain Bowl", "Kitchen", "Farro, greens, citrus tahini", 1_400, R.drawable.product_grainbowl),
        MerchantProduct("tartine", "Smoked Salmon Tartine", "Kitchen", "Rye, crème fraîche, dill", 1_250, R.drawable.product_tartine),
        MerchantProduct("croissant", "Butter Croissant", "Bakery", "Laminated overnight", 380, R.drawable.product_croissant),
        MerchantProduct("morning-bun", "Almond Morning Bun", "Bakery", "Orange blossom, toasted nuts", 440, R.drawable.product_morningbun),
        MerchantProduct("loaf", "Citrus Loaf", "Bakery", "Olive oil, slice", 410, R.drawable.product_loaf),
    ),
)

internal val PulseBrand = MerchantBrand(
    name = "Pulse",
    tagline = "Studio memberships and classes. Embedded checkout.",
    emptyHint = "Choose a pack or a drop-in, then pay with the form on the next screen.",
    catalogStyle = MerchantCatalogStyle.Plans,
    paySurface = MerchantPaySurface.Embedded,
    appearance = exampleAppearance(
        primary = ExampleColors.LimeDark,
        primaryDark = ExampleColors.Lime,
        buttonBackground = ExampleColors.Lime,
        buttonText = ExampleColors.LimeDark,
    ),
    accent = ExampleColors.Lime,
    onAccent = ExampleColors.LimeDark,
    accentText = ExampleColors.LimeDark,
    products = listOf(
        MerchantProduct("unlimited", "Monthly Unlimited", "Membership", "All classes, guest pass once a month", 7_900, R.drawable.product_pulse_unlimited),
        MerchantProduct("pack-5", "5-Class Pack", "Packs", "Use within 8 weeks", 9_500, R.drawable.product_pulse_pack),
        MerchantProduct("drop-in", "Drop-in Class", "Classes", "Any public session today", 2_200, R.drawable.product_pulse_dropin),
        MerchantProduct("reformer", "Reformer Intro", "Classes", "50 minutes, small group", 4_500, R.drawable.product_pulse_reformer),
        MerchantProduct("recovery", "Recovery Session", "Wellness", "Stretch + breathwork", 3_800, R.drawable.product_pulse_recovery),
        MerchantProduct("swim", "Lane Swim Pass", "Wellness", "Morning lanes, 10 entries", 6_000, R.drawable.product_pulse_swim),
        MerchantProduct("heart", "Heart-Rate Lab", "Classes", "Guided intervals, 40 minutes", 2_800, R.drawable.product_pulse_heart),
    ),
)
