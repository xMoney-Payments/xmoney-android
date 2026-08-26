package com.xmoney.example.playground

internal data class DemoOption(val label: String, val value: String)

internal enum class IntegrationMode(val label: String) {
    PaymentSheet("Sheet"),
    GooglePay("Google Pay"),
    Embedded("Embedded"),
}

enum class PaletteSlot {
    Shared,
    Light,
    Dark,
}

internal val playgroundLocaleOptions = listOf(
    DemoOption("English", "en-US"),
    DemoOption("Greek", "el-GR"),
    DemoOption("Romanian", "ro-RO"),
    DemoOption("Bulgarian", "bg-BG"),
    DemoOption("Hungarian", "hu-HU"),
    DemoOption("Polish", "pl-PL"),
)

internal val playgroundStyleOptions = listOf(
    DemoOption("Auto", "automatic"),
    DemoOption("Light", "alwaysLight"),
    DemoOption("Dark", "alwaysDark"),
)

internal val playgroundPaletteOptions = listOf(
    DemoOption("Shared", PaletteSlot.Shared.name),
    DemoOption("Light", PaletteSlot.Light.name),
    DemoOption("Dark", PaletteSlot.Dark.name),
)

internal val playgroundButtonTypeOptions = listOf(
    DemoOption("Pay", "pay"),
    DemoOption("Book", "book"),
    DemoOption("Buy", "buy"),
    DemoOption("Checkout", "checkout"),
    DemoOption("Donate", "donate"),
    DemoOption("Order", "order"),
    DemoOption("Subscribe", "subscribe"),
    DemoOption("Top up", "topUp"),
    DemoOption("Deposit", "deposit"),
)

internal val playgroundValidationOptions = listOf(
    DemoOption("On touched", "onTouched"),
    DemoOption("On change", "onChange"),
    DemoOption("On blur", "onBlur"),
    DemoOption("On submit", "onSubmit"),
)

internal val playgroundGroupingOptions = listOf(
    DemoOption("Condensed", "condensed"),
    DemoOption("Spaced", "spaced"),
)

internal val playgroundWalletColorOptions = listOf(
    DemoOption("Auto", "auto"),
    DemoOption("Black", "black"),
    DemoOption("White", "white"),
)

internal val playgroundWalletTypeOptions = listOf(
    DemoOption("Pay", "pay"),
    DemoOption("Plain", "plain"),
    DemoOption("Buy", "buy"),
    DemoOption("Book", "book"),
    DemoOption("Checkout", "checkout"),
    DemoOption("Donate", "donate"),
    DemoOption("Order", "order"),
    DemoOption("Subscribe", "subscribe"),
)

internal val playgroundFontFamilyOptions = listOf(
    DemoOption("Default (Roobert)", ""),
    DemoOption("Sans serif", "sans-serif"),
    DemoOption("Sans serif medium", "sans-serif-medium"),
    DemoOption("Sans serif light", "sans-serif-light"),
    DemoOption("Serif", "serif"),
    DemoOption("Monospace", "monospace"),
    DemoOption("Casual", "casual"),
    DemoOption("Cursive", "cursive"),
)

internal fun List<DemoOption>.option(value: String): DemoOption =
    firstOrNull { it.value == value } ?: first()
