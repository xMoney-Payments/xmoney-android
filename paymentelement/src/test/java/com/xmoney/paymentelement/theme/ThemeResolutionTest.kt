package com.xmoney.paymentelement.theme

import com.xmoney.payments.config.Appearance
import com.xmoney.payments.config.AppearanceOverrides
import com.xmoney.payments.config.Borders
import com.xmoney.payments.config.Corners
import com.xmoney.payments.config.Shape
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.ThemeColor
import androidx.compose.ui.graphics.luminance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolutionTest {
    private fun resolve(
        appearance: Appearance = Appearance(),
        isDark: Boolean,
        increasedContrast: Boolean = false,
    ): CheckoutTheme {
        val config = ResolvedPaymentConfig(
            publicKey = "pk_test",
            orderPayload = "payload",
            orderChecksum = "checksum",
            options = OptionsConfig(appearance = appearance),
        )
        return CheckoutTheme.resolve(config, isDark, increasedContrast = increasedContrast)
    }

    private fun assertClose(expected: String, actual: String) {
        fun channels(hex: String) = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) }
        channels(expected).zip(channels(actual)).forEach { (want, got) ->
            org.junit.Assert.assertTrue("$actual is not within 1 of $expected", kotlin.math.abs(want - got) <= 1)
        }
    }

    private fun hex(color: androidx.compose.ui.graphics.Color): String {
        fun channel(value: Float) = kotlin.math.round(value * 255).toInt()
        return String.format("#%02x%02x%02x", channel(color.red), channel(color.green), channel(color.blue))
    }

    @Test
    fun emptyAppearanceMatchesBuiltInThemes() {
        val light = resolve(isDark = false)
        val dark = resolve(isDark = true)
        assertEquals("#f7f6ff", hex(light.background))
        assertEquals("#ffffff", hex(light.componentBackground))
        assertEquals("#7c4dff", hex(light.primary))
        assertEquals("#16141a", hex(light.primaryText))
        assertEquals("#7a4bfb", hex(light.textBrand))
        assertEquals("#dc2626", hex(light.errorText))
        assertEquals("#18181b", hex(dark.background))
        assertEquals("#1f1f23", hex(dark.componentBackground))
        assertEquals("#fafafa", hex(dark.primaryText))
        assertEquals("#926bff", hex(dark.textBrand))
        assertEquals("#ef4444", hex(dark.errorText))
        assertEquals("#7c4dff", hex(light.iconBrand))
        assertEquals("#926bff", hex(dark.iconBrand))
        assertEquals("#f7f6ff", hex(light.inverseText))
        assertEquals("#09090b", hex(dark.inverseText))
        assertEquals("#ffffff", hex(light.brandTileBackground))
        assertEquals("#ffffff", hex(dark.brandTileBackground))
        assertEquals(null, light.visaMarkTint)
        assertEquals(null, dark.visaMarkTint)
        assertEquals(16f, light.rowRadius.value)
        assertEquals(16f, dark.rowRadius.value)
        assertEquals(12f, light.brandTileRadius.value)
        assertEquals(12f, dark.brandTileRadius.value)
        assertEquals(0f, light.containerBorder.alpha, 0.001f)
        assertEquals(0f, dark.containerBorder.alpha, 0.001f)
    }

    @Test
    fun sentBrandIsNotRewritten() {
        val yellow = resolve(Appearance(brandColor = ThemeColor.of("#FACC15")), false)
        assertEquals("#facc15", hex(yellow.primary))
        assertEquals("#16141a", hex(yellow.primaryButtonText))
        val blue = resolve(Appearance(brandColor = ThemeColor.of("#1D4ED8")), false)
        assertEquals("#ffffff", hex(blue.primaryButtonText))
        assertEquals("#f1f4fd", hex(blue.background))
        assertEquals("#1d4ed8", hex(blue.iconBrand))
    }

    @Test
    fun warmCreamCardIsWhiteOverTheSheet() {
        val theme = resolve(
            Appearance(
                brandColor = ThemeColor.of("#B4532A"),
                backgroundColor = ThemeColor.of("#FBF5EA"),
                neutralTint = ThemeColor.of("#8A7560"),
            ),
            false,
        )
        assertEquals("#fbf5ea", hex(theme.background))
        assertClose("#fefdfa", hex(theme.componentBackground))

        val dark = resolve(
            Appearance(
                brandColor = ThemeColor.of("#B4532A", "#F0B090"),
                backgroundColor = ThemeColor.of("#FBF5EA", "#140E0A"),
                neutralTint = ThemeColor.of("#8A7560", "#CDB59A"),
            ),
            true,
        )
        assertEquals("#40362d", hex(dark.background))
        assertEquals("#53473b", hex(dark.componentBackground))
        assertEquals("#695b4c", hex(dark.selectedBackground))
        assertEquals("#f0b090", hex(dark.primary))
        assertEquals("#16141a", hex(dark.primaryButtonText))
    }

    @Test
    fun blackSheetLiftsOffTheBackdrop() {
        val theme = resolve(
            Appearance(
                brandColor = ThemeColor.of("#FFFFFF"),
                backgroundColor = ThemeColor.of("#000000"),
                neutralTint = ThemeColor.of("#7B909A"),
            ),
            true,
        )
        assertEquals("#1e2325", hex(theme.background))
        assertEquals("#2a3134", hex(theme.componentBackground))
        assertEquals("#394247", hex(theme.selectedBackground))
    }

    @Test
    fun overridesAreExact() {
        val theme = resolve(
            Appearance(
                overrides = AppearanceOverrides(
                    onBrandColor = ThemeColor.of("#111111"),
                    textColor = ThemeColor.of("#222222"),
                    componentColor = ThemeColor.of("#EEEEEE"),
                    borderColor = ThemeColor.of("#ABCDEF"),
                ),
            ),
            false,
        )
        assertEquals("#111111", hex(theme.primaryButtonText))
        assertEquals("#222222", hex(theme.primaryText))
        assertEquals("#eeeeee", hex(theme.componentBackground))
        assertEquals("#abcdef", hex(theme.fieldBorder))
    }

    @Test
    fun lowContrastTextColorIsKept() {
        val theme = resolve(
            Appearance(overrides = AppearanceOverrides(textColor = ThemeColor.of("#FACC15"))),
            false,
        )
        assertEquals("#facc15", hex(theme.primaryText))
        assertEquals("#facc15", hex(theme.sheetText))
    }

    @Test
    fun lowContrastOverridesStayExact() {
        val yellow = ThemeColor.of("#FACC15")
        val theme = resolve(
            Appearance(
                overrides = AppearanceOverrides(
                    onBrandColor = yellow,
                    textColor = yellow,
                    linkColor = yellow,
                    borderColor = yellow,
                    componentColor = yellow,
                    selectedColor = yellow,
                    secondaryButtonColor = yellow,
                ),
            ),
            false,
        )
        assertEquals("#facc15", hex(theme.primaryButtonText))
        assertEquals("#facc15", hex(theme.primaryText))
        assertEquals("#facc15", hex(theme.sheetText))
        assertEquals("#facc15", hex(theme.textBrand))
        assertEquals("#facc15", hex(theme.fieldBorder))
        assertEquals("#facc15", hex(theme.componentBackground))
        assertEquals("#facc15", hex(theme.selectedBackground))
        assertEquals("#facc15", hex(theme.secondaryButtonBackground))
    }

    @Test
    fun borderWidthDrivesTheIdleStrokeAndKeepsErrorAndFocusVisible() {
        assertEquals(1f, resolve(isDark = false).borderWidth.value)
        val none = resolve(Appearance(borders = Borders(width = 0.0)), false)
        assertEquals(0f, none.fieldStrokeWidth(hasError = false).value)
        assertTrue(none.fieldStrokeWidth(hasError = true).value >= 1.5f)
        assertTrue(none.fieldStrokeWidth(hasError = false, focused = true).value >= 1.5f)
        assertEquals(0f, none.containerBorderWidth.value)
        val wide = resolve(
            Appearance(
                overrides = AppearanceOverrides(borderColor = ThemeColor.of("#111111")),
                borders = Borders(width = 2.0),
            ),
            false,
        )
        assertEquals(2f, wide.borderWidth.value)
        assertEquals(2f, wide.containerBorderWidth.value)
        val hidden = resolve(
            Appearance(
                overrides = AppearanceOverrides(borderColor = ThemeColor.of("#111111")),
                borders = Borders(width = 0.0),
            ),
            false,
        )
        assertEquals(0f, hidden.containerBorderWidth.value)
        assertEquals(4f, resolve(Appearance(borders = Borders(width = 9.0)), false).borderWidth.value)
        assertEquals(2.5, Appearance.from(mapOf("borders" to mapOf("width" to 2.5))).borders.width, 0.001)
        assertEquals(4.0, Appearance.from(mapOf("borders" to mapOf("width" to 9))).borders.width, 0.001)
        assertEquals(0.0, Appearance.from(mapOf("borders" to mapOf("width" to -1))).borders.width, 0.001)
        assertEquals(1.0, Appearance.from(mapOf("borders" to mapOf("width" to true))).borders.width, 0.001)
        val flat = Appearance.from(mapOf("fontFamily" to "Georgia", "borderWidth" to 3, "corners" to "sharp", "pillButtons" to false))
        assertEquals(null, flat.typography.fontFamily)
        assertEquals(1.0, flat.borders.width, 0.001)
        assertEquals(Corners.Default, flat.shape.corners)
        assertEquals(true, flat.shape.pillButtons)
    }

    @Test
    fun unusualPalettesKeepGuaranteedContrast() {
        // withContrast stops a hair under its target, and the built-in dark section
        // token (#85858D on #1F1F23) is 4.49:1. Sheet text, secondary, meta, and the
        // pay label clear 4.5. The lower floors lock that miss without changing it.
        val palettes = listOf(
            "near-white brand" to Appearance(brandColor = ThemeColor.of("#FAFAFA")),
            "near-black brand" to Appearance(brandColor = ThemeColor.of("#16141A")),
            "yellow" to Appearance(brandColor = ThemeColor.of("#FACC15")),
            "neon" to Appearance(brandColor = ThemeColor.of("#00FF00")),
            "mid-gray" to Appearance(brandColor = ThemeColor.of("#808080")),
            "white on black" to Appearance(
                brandColor = ThemeColor.of("#FFFFFF"),
                backgroundColor = ThemeColor.of("#000000"),
            ),
            "cream" to Appearance(backgroundColor = ThemeColor.of("#FBF5EA")),
            "dark tint on light" to Appearance(
                backgroundColor = ThemeColor.of("#FBF5EA"),
                neutralTint = ThemeColor.of("#2A2118"),
            ),
        )
        for ((name, appearance) in palettes) {
            for (isDark in listOf(false, true)) {
                for (increased in listOf(false, true)) {
                    val theme = resolve(appearance, isDark, increasedContrast = increased)
                    val where = "$name dark=$isDark increased=$increased"
                    assertContrast(where, "sheet text", theme.sheetText, theme.background, 4.5f)
                    assertContrast(where, "secondary", theme.secondaryText, theme.componentBackground, 4.5f)
                    assertContrast(where, "meta", theme.metaText, theme.componentBackground, 4.5f)
                    assertContrast(where, "section", theme.sectionLabel, theme.componentBackground, 4.45f)
                    assertContrast(where, "placeholder", theme.placeholderText, theme.componentBackground, 2.95f)
                    assertContrast(where, "or", theme.orLabel, theme.background, 4.45f)
                    assertContrast(where, "disabled pay", theme.primaryButtonDisabledText, theme.primaryButtonDisabledBackground, 4.45f)
                    assertContrast(where, "pay label", theme.primaryButtonText, theme.primaryButtonBackground, 4.5f)
                }
            }
        }
    }

    @Test
    fun cornersAtTenAndPillButtons() {
        val theme = resolve(Appearance(shape = Shape(corners = Corners.Custom(10.0))), false)
        assertEquals(10f, theme.formFieldRadius.value)
        assertEquals(8f, theme.rowRadius.value)
        assertEquals(5f, theme.checkboxRadius.value)
        assertEquals(15f, theme.paymentContainerRadius.value)
        assertEquals(32f, theme.sheetCornerRadius.value)
        val square = resolve(Appearance(shape = Shape(Corners.Sharp, pillButtons = false)), false)
        assertEquals(0f, square.primaryButtonBorderRadius.value)
    }

    @Test
    fun idleRingsStayTranslucentAndChecksFollowTheLabel() {
        val dark = resolve(isDark = true)
        assertNotEquals("#fafafa", hex(dark.radioIdle))
        assertNotEquals(hex(dark.primary), hex(dark.radioIdle))
        assertEquals(dark.unselectedRing, dark.radioIdle)
        assertEquals("#fafafa", hex(dark.stateIndicatorBorder))
        assertEquals(0.63f, dark.radioIdle.alpha, 0.02f)
        assertEquals(1f, dark.stateIndicatorBorder.alpha, 0.01f)

        val light = resolve(isDark = false)
        assertEquals(0.80f, light.radioIdle.alpha, 0.02f)
        assertEquals("#16141a", hex(light.stateIndicatorBorder))

        val white = resolve(Appearance(brandColor = ThemeColor.of("#FFFFFF")), true)
        assertEquals("#16141a", hex(white.primaryButtonText))
        assertEquals(0.63f, white.radioIdle.alpha, 0.02f)

        val blue = resolve(Appearance(brandColor = ThemeColor.of("#1D4ED8")), false)
        assertEquals("#ffffff", hex(blue.primaryButtonText))

        val yellow = resolve(Appearance(brandColor = ThemeColor.of("#FACC15")), false)
        assertEquals("#16141a", hex(yellow.primaryButtonText))
    }

    @Test
    fun disabledFillIsOpaque() {
        val theme = resolve(Appearance(brandColor = ThemeColor.of("#1D4ED8")), false)
        assertEquals(1f, theme.primaryButtonDisabledBackground.alpha)
        assertNotEquals(hex(theme.primary), hex(theme.primaryButtonDisabledBackground))
    }

    @Test
    fun parsingClampsCornersAndKeepsKnownKeys() {
        val appearance = Appearance.from(
            mapOf(
                "brandColor" to "#1D4ED8",
                "shape" to mapOf("corners" to 40, "pillButtons" to false, "mystery" to true),
                "typography" to mapOf("fontFamily" to "Georgia"),
                "fontFamily" to "Georgia",
                "colorScheme" to "dark",
            ),
        )
        assertEquals("#1D4ED8", String.format("#%06X", appearance.brandColor!!.light and 0xFFFFFF))
        assertEquals(24.0, appearance.shape.corners.scale, 0.001)
        assertEquals("Georgia", appearance.typography.fontFamily)
        assertEquals(false, appearance.shape.pillButtons)
    }

    @Test
    fun sheetRadiusStaysAtTheDefault() {
        assertEquals(32f, resolve(Appearance(shape = Shape(corners = Corners.Sharp)), false).sheetCornerRadius.value)
        assertEquals(32f, resolve(Appearance(shape = Shape(corners = Corners.Rounded)), true).sheetCornerRadius.value)
        assertEquals(0f, resolve(Appearance(shape = Shape(corners = Corners.Sharp)), false).brandTileRadius.value)
        assertEquals(12f, resolve(Appearance(shape = Shape(corners = Corners.Rounded)), true).brandTileRadius.value)
    }

    @Test
    fun backgroundAloneDoesNotStrokeMethodCards() {
        val theme = resolve(Appearance(backgroundColor = ThemeColor.of("#000000")), true)
        assertEquals(0f, theme.containerBorder.alpha, 0.001f)
        val stroked = resolve(
            Appearance(overrides = AppearanceOverrides(borderColor = ThemeColor.of("#ABCDEF"))),
            false,
        )
        assertTrue(stroked.containerBorder.alpha > 0.5f)
    }

    @Test
    fun yellowLinkAndFocusLeaveTheRawBrand() {
        val theme = resolve(Appearance(brandColor = ThemeColor.of("#FACC15")), false)
        assertEquals("#facc15", hex(theme.primary))
        assertNotEquals("#facc15", hex(theme.focusRing))
        assertEquals("#facc15", hex(theme.textBrand))
        assertEquals("#facc15", hex(theme.iconBrand))
        assertEquals("#fffcf1", hex(theme.background))
        assertEquals("#fffcf1", hex(theme.selectedBackground))
        assertEquals("#fef7dc", hex(theme.accentIconBackground))
        val dark = resolve(Appearance(brandColor = ThemeColor.of("#FACC15")), true)
        assertEquals("#facc15", hex(dark.primary))
        assertEquals("#fbd53d", hex(dark.textBrand))
        assertEquals("#fbd53d", hex(dark.iconBrand))
        assertEquals("#18181b", hex(dark.background))
        val teal = resolve(Appearance(brandColor = ThemeColor.of("#0F766E")), true)
        assertEquals("#388d87", hex(teal.textBrand))
        assertEquals("#388d87", hex(teal.iconBrand))
    }

    @Test
    fun disabledLabelClearsTheFill() {
        val theme = resolve(isDark = false)
        assertTrue(contrast(theme.primaryButtonDisabledText, theme.primaryButtonDisabledBackground) >= 4.5f)
    }

    @Test
    fun whiteCardInDarkModeUsesDarkFieldText() {
        val theme = resolve(
            Appearance(overrides = AppearanceOverrides(componentColor = ThemeColor.of("#FFFFFF"))),
            true,
        )
        assertEquals("#ffffff", hex(theme.componentBackground))
        assertEquals("#16141a", hex(theme.primaryText))
        assertCardSideContrast(theme)
        assertTrue(contrast(theme.sheetText, theme.background) >= 4.5f)
    }

    @Test
    fun darkCardInLightModeKeepsLightSheetText() {
        val theme = resolve(
            Appearance(overrides = AppearanceOverrides(componentColor = ThemeColor.of("#16141A"))),
            false,
        )
        assertCardSideContrast(theme)
        assertTrue(contrast(theme.sheetText, theme.background) >= 4.5f)
        assertFalse(theme.isDarkSurface)
    }

    @Test
    fun lightBackgroundUsesTheDarkWalletStyle() {
        val theme = resolve(Appearance(backgroundColor = ThemeColor.of("#FFFFFF")), true)
        assertFalse(theme.isDarkSurface)
    }

    @Test
    fun themeResolutionVectors() {
        val json = javaClass.classLoader!!.getResourceAsStream("test-vectors.json")!!
            .readBytes().toString(Charsets.UTF_8)
        val cases = JSONObject(json).getJSONArray("themeResolution")
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val item = cases.getJSONObject(i)
            val theme = resolve(Appearance.from(jsonToMap(item.getJSONObject("appearance"))), item.getBoolean("isDark"))
            val expected = item.getJSONObject("expected")
            val keys = expected.keys()
            while (keys.hasNext()) {
                when (val key = keys.next()) {
                    "primary" -> assertEquals(expected.getString(key), hex(theme.primary))
                    "background" -> assertEquals(expected.getString(key), hex(theme.background))
                    else -> error("unhandled theme vector key $key")
                }
            }
            val brand = item.getJSONObject("appearance").optString("brandColor")
            if (brand.length == 9) {
                val parsed = com.xmoney.payments.config.SrgbColor.fromHex(brand)
                assertEquals(128.0 / 255.0, parsed!!.alpha, 0.001)
                assertEquals(1.0, parsed.red, 0.001)
            }
        }
        val defaults = JSONObject(json).getJSONObject("defaultColors")
        assertEquals("#dc2626", defaults.getJSONObject("light").getString("error"))
        assertEquals("#ef4444", defaults.getJSONObject("dark").getString("error"))
        assertEquals(defaults.getJSONObject("light").getString("error"), hex(resolve(isDark = false).errorText))
        assertEquals(defaults.getJSONObject("dark").getString("error"), hex(resolve(isDark = true).errorText))
    }

    @Test
    fun increasedContrastLiftsDefaultMetaText() {
        val config = ResolvedPaymentConfig(
            publicKey = "pk_test",
            orderPayload = "payload",
            orderChecksum = "checksum",
            options = OptionsConfig(),
        )
        for (dark in listOf(false, true)) {
            val theme = CheckoutTheme.resolve(config, dark, increasedContrast = true)
            fun check(name: String, foreground: androidx.compose.ui.graphics.Color, surface: androidx.compose.ui.graphics.Color) {
                val ratio = contrast(foreground, surface)
                assertTrue(
                    "$name dark=$dark ratio=$ratio ${hex(foreground)} on ${hex(surface)}",
                    ratio >= 4.5f,
                )
            }
            check("secondary", theme.secondaryText, theme.selectedBackground)
            check("placeholder", theme.placeholderText, theme.componentBackground)
            check("powered", theme.poweredByText, theme.background)
            check("or", theme.orLabel, theme.background)
        }
    }

    @Test
    fun emptyAppearanceContainerRadiusIs24() {
        assertEquals(24f, resolve(isDark = false).paymentContainerRadius.value)
        assertEquals(24f, resolve(isDark = true).paymentContainerRadius.value)
    }

    private fun assertCardSideContrast(theme: CheckoutTheme) {
        assertTrue(contrast(theme.primaryText, theme.selectedBackground) >= 4.5f)
        assertTrue(contrast(theme.metaText, theme.componentBackground) >= 4.5f)
        assertTrue(contrast(theme.inverseText, theme.primaryText) >= 4.5f)
        assertTrue(contrast(theme.textBrand, theme.componentBackground) >= 4.5f)
        assertTrue(contrast(theme.focusRing, theme.componentBackground) >= 3f)
        assertTrue(contrast(theme.focusRing, theme.background) >= 3f)
    }

    @Test
    fun customCornerValueIsNotShared() {
        val eight = Corners.from(8)
        val four = Corners.from(4)
        assertEquals(8.0, eight.scale, 0.001)
        assertEquals(4.0, four.scale, 0.001)
        assertEquals(8.0, Appearance(shape = Shape(corners = Corners.Custom(8.0))).shape.corners.scale, 0.001)
    }

    private fun jsonToMap(source: JSONObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = source.get(key)
            map[key] = if (value is JSONObject) jsonToMap(value) else value
        }
        return map
    }

    private fun assertContrast(
        palette: String,
        role: String,
        color: androidx.compose.ui.graphics.Color,
        surface: androidx.compose.ui.graphics.Color,
        minimum: Float,
    ) {
        val ratio = contrast(color, surface)
        assertTrue("$palette $role $ratio ${hex(color)} on ${hex(surface)}", ratio >= minimum)
    }

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
