package com.xmoney.paymentelement.theme

import com.xmoney.payments.config.Appearance
import com.xmoney.payments.config.AppearanceOverrides
import com.xmoney.payments.config.Corners
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
    private fun resolve(appearance: Appearance = Appearance(), isDark: Boolean): CheckoutTheme {
        val config = ResolvedPaymentConfig(
            publicKey = "pk_test",
            orderPayload = "payload",
            orderChecksum = "checksum",
            options = OptionsConfig(appearance = appearance),
        )
        return CheckoutTheme.resolve(config, isDark)
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
    }

    @Test
    fun xPortalSheetLiftsOffTheBackdrop() {
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
    fun cornersAtTenAndPillButtons() {
        val theme = resolve(Appearance(corners = Corners.Custom(10.0)), false)
        assertEquals(10f, theme.formFieldRadius.value)
        assertEquals(8f, theme.rowRadius.value)
        assertEquals(5f, theme.checkboxRadius.value)
        assertEquals(15f, theme.paymentContainerRadius.value)
        assertEquals(32f, theme.sheetCornerRadius.value)
        val square = resolve(Appearance(corners = Corners.Sharp, pillButtons = false), false)
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
        assertFalse(dark.filledBrandNeedsOutline)

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
        assertFalse(yellow.filledBrandNeedsOutline)
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
                "corners" to 40,
                "mystery" to true,
                "fontFamily" to "Georgia",
                "pillButtons" to false,
                "colorScheme" to "dark",
            ),
        )
        assertEquals("#1D4ED8", String.format("#%06X", appearance.brandColor!!.light and 0xFFFFFF))
        assertEquals(24.0, appearance.corners.scale, 0.001)
        assertEquals("Georgia", appearance.fontFamily)
        assertEquals(false, appearance.pillButtons)
    }

    @Test
    fun sheetRadiusStaysAtTheDefault() {
        assertEquals(32f, resolve(Appearance(corners = Corners.Sharp), false).sheetCornerRadius.value)
        assertEquals(32f, resolve(Appearance(corners = Corners.Rounded), true).sheetCornerRadius.value)
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
            if (expected.has("primary")) assertEquals(expected.getString("primary"), hex(theme.primary))
            if (expected.has("background")) assertEquals(expected.getString("background"), hex(theme.background))
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
        assertEquals(8.0, Appearance(corners = Corners.Custom(8.0)).corners.scale, 0.001)
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

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
