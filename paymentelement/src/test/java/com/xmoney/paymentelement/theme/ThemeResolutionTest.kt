package com.xmoney.paymentelement.theme

import androidx.compose.ui.graphics.Color
import com.xmoney.payments.config.AppearanceConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class ThemeResolutionTest {
    private val vectors: JSONObject = loadVectors()

    @Test
    fun themeResolutionVectors() {
        val cases = vectors.getJSONArray("themeResolution")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val isDark = case.getBoolean("isDark")
            val appearanceMap = jsonToMap(case.getJSONObject("appearance"))
            val expected = case.getJSONObject("expected")

            val config = ResolvedPaymentConfig(
                publicKey = "pk_test",
                orderPayload = "",
                orderChecksum = "",
                options = OptionsConfig(appearance = AppearanceConfig.from(appearanceMap)),
            )
            val theme = CheckoutTheme.resolve(config, isDark)

            expected.keys().forEach { key ->
                val hex = expected.getString(key)
                val actual = when (key) {
                    "primary" -> theme.primary
                    "background" -> theme.background
                    "error" -> theme.error
                    "selectedBackground" -> theme.selectedBackground
                    "accentIconBackground" -> theme.accentIconBackground
                    "containerBorder" -> theme.containerBorder
                    else -> error("Unsupported theme key: $key")
                }
                assertEquals("case $i $key", requireColor(hex), actual)
            }
        }
    }

    @Test
    fun selectedWashesDeriveFromPrimary() {
        val primaryHex = "#0E7C66"
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("primary" to primaryHex)),
            isDark = false,
        )
        val primary = requireColor(primaryHex)
        assertEquals(primary.copy(alpha = 0x0F / 255f), theme.selectedBackground)
        assertEquals(primary.copy(alpha = 0x1F / 255f), theme.accentIconBackground)
    }

    @Test
    fun darkSelectedWashesUseStrongerPrimaryAlpha() {
        val primaryHex = "#0E7C66"
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("primary" to primaryHex)),
            isDark = true,
        )
        val primary = requireColor(primaryHex)
        assertEquals(primary.copy(alpha = 0x2E / 255f), theme.selectedBackground)
        assertEquals(primary.copy(alpha = 0x3D / 255f), theme.accentIconBackground)
    }

    @Test
    fun containerBorderNoneIsTransparent() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("containerBorder" to "none")),
            isDark = false,
        )
        assertEquals(Color.Transparent, theme.containerBorder)
        assertEquals(0f, theme.containerBorderWidth.value)
    }

    @Test
    fun containerBorderTransparentSentinel() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("containerBorder" to "transparent")),
            isDark = false,
        )
        assertEquals(Color.Transparent, theme.containerBorder)
    }

    @Test
    fun containerBorderHexOverride() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("containerBorder" to "#AABBCC")),
            isDark = false,
        )
        assertEquals(requireColor("#AABBCC"), theme.containerBorder)
        assertEquals(1f, theme.containerBorderWidth.value)
    }

    @Test
    fun primaryButtonDefaultsToPill() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(CheckoutTheme.DefaultPrimaryButtonRadius, theme.primaryButtonBorderRadius.value, 0.001f)
    }

    @Test
    fun primaryButtonShapesBorderRadiusReachesTheme() {
        val theme = resolveAppearance(
            mapOf(
                "primaryButton" to mapOf(
                    "shapes" to mapOf("borderRadius" to 8),
                ),
            ),
            isDark = false,
        )
        assertEquals(8f, theme.primaryButtonBorderRadius.value, 0.001f)
    }

    @Test
    fun omittedBorderRadiusKeepsBrandedFieldAndContainerDefaults() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(CheckoutTheme.DefaultFormFieldRadius, theme.formFieldRadius.value, 0.001f)
        assertEquals(CheckoutTheme.DefaultPaymentContainerRadius, theme.paymentContainerRadius.value, 0.001f)
        assertEquals(1f, theme.borderWidth.value, 0.001f)
        assertEquals(1f, theme.fieldStrokeWidth(false).value, 0.001f)
        assertEquals(1.5f, theme.fieldStrokeWidth(true).value, 0.001f)
    }

    @Test
    fun appearanceShapesReachFieldsAndContainer() {
        val theme = resolveAppearance(
            mapOf("shapes" to mapOf("borderRadius" to 4, "borderWidth" to 2.5)),
            isDark = false,
        )
        assertEquals(4f, theme.formFieldRadius.value, 0.001f)
        assertEquals(4f, theme.paymentContainerRadius.value, 0.001f)
        assertEquals(2.5f, theme.borderWidth.value, 0.001f)
        assertEquals(2.5f, theme.fieldStrokeWidth(false).value, 0.001f)
        assertEquals(2.5f, theme.fieldStrokeWidth(true).value, 0.001f)
    }

    @Test
    fun zeroBorderWidthStillShowsErrorRing() {
        val theme = resolveAppearance(
            mapOf("shapes" to mapOf("borderWidth" to 0)),
            isDark = false,
        )
        assertEquals(0f, theme.fieldStrokeWidth(false).value, 0.001f)
        assertEquals(1.5f, theme.fieldStrokeWidth(true).value, 0.001f)
    }

    @Test
    fun componentBorderOverridesFieldBorder() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("componentBorder" to "#AABBCC")),
            isDark = false,
        )
        assertEquals(requireColor("#AABBCC"), theme.fieldBorder)
    }

    @Test
    fun componentDividerOverridesFieldDivider() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("componentDivider" to "#112233")),
            isDark = false,
        )
        assertEquals(requireColor("#112233"), theme.fieldDivider)
    }

    @Test
    fun errorColorOverridesFieldErrorChrome() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("error" to "#00AA55")),
            isDark = false,
        )
        val error = requireColor("#00AA55")
        assertEquals(error, theme.errorBorder)
        assertEquals(error, theme.errorText)
    }

    @Test
    fun omittedErrorKeepsBrandedFieldChrome() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(Color(0xFFEF4444), theme.errorBorder)
        assertEquals(Color(0xFFDC2626), theme.errorText)
    }

    @Test
    fun brandPrimaryIsXMoneyPurple() {
        assertEquals(Color(0xFF7C4DFF), CheckoutTheme.BrandPrimary)
    }

    @Test
    fun containerBorderDefaultsWhenOmitted() {
        val light = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(Color(0x1716141A), light.containerBorder)

        val dark = resolveAppearance(emptyMap(), isDark = true)
        assertEquals(Color(0xFFF7F6F9).copy(alpha = 0x17 / 255f), dark.containerBorder)
    }

    @Test
    fun darkModeChromeUsesLightInk() {
        val theme = resolveAppearance(emptyMap(), isDark = true)
        val ink = Color(0xFFF7F6F9)
        val chrome = Color.White
        assertEquals(Color(0xFF18181B), theme.background)
        assertEquals(Color(0xFF18181B), theme.componentBackground)
        assertEquals(Color(0xFF797585), theme.mutedIcon)
        assertEquals(chrome.copy(alpha = 0x33 / 255f), theme.unselectedRing)
        assertEquals(ink.copy(alpha = 0x1A / 255f), theme.fieldBorder)
        assertEquals(ink.copy(alpha = 0x14 / 255f), theme.fieldDivider)
        assertEquals(chrome.copy(alpha = 0x29 / 255f), theme.sheetHandle)
        assertEquals(chrome.copy(alpha = 0x1A / 255f), theme.orDivider)
        assertEquals(chrome.copy(alpha = 0x14 / 255f), theme.footerBorder)
    }

    @Test
    fun lightModeChromePreserved() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        val ink = Color(0xFF16141A)
        assertEquals(Color(0x5216141A), theme.mutedIcon)
        assertEquals(Color(0x2916141A), theme.unselectedRing)
        assertEquals(Color(0x1A16141A), theme.fieldBorder)
        assertEquals(Color(0x1416141A), theme.fieldDivider)
        assertEquals(ink.copy(alpha = 0x1F / 255f), theme.sheetHandle)
        assertEquals(ink.copy(alpha = 0x1A / 255f), theme.orDivider)
        assertEquals(ink.copy(alpha = 0x0F / 255f), theme.footerBorder)
        assertEquals(null, theme.visaMarkTint)
    }

    @Test
    fun darkVisaMarkIsWhite() {
        val theme = resolveAppearance(emptyMap(), isDark = true)
        assertEquals(Color.White, theme.visaMarkTint)
    }

    @Test
    fun darkMutedIconRespectsAppearanceOverride() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("icon" to "#AABBCC")),
            isDark = true,
        )
        assertEquals(requireColor("#AABBCC"), theme.mutedIcon)
    }

    @Test
    fun fontFamilyDefaultsToRoobertWhenOmitted() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(PaymentFontFamily.family, theme.fontFamily)
        assertEquals(PaymentFontFamily.family, theme.primaryButtonFontFamily)
    }

    @Test
    fun parseColorOrNone() {
        assertEquals(Color.Transparent, CheckoutTheme.parseColorOrNone("none"))
        assertEquals(Color.Transparent, CheckoutTheme.parseColorOrNone("NONE"))
        assertEquals(Color.Transparent, CheckoutTheme.parseColorOrNone("transparent"))
        assertEquals(requireColor("#112233"), CheckoutTheme.parseColorOrNone("#112233"))
        assertEquals(null, CheckoutTheme.parseColorOrNone(null))
        assertEquals(null, CheckoutTheme.parseColorOrNone(""))
        assertEquals(null, CheckoutTheme.parseColorOrNone("not-a-color"))
    }

    private fun resolveAppearance(appearance: Map<String, Any?>, isDark: Boolean): CheckoutTheme {
        val config = ResolvedPaymentConfig(
            publicKey = "pk_test",
            orderPayload = "",
            orderChecksum = "",
            options = OptionsConfig(appearance = AppearanceConfig.from(appearance)),
        )
        return CheckoutTheme.resolve(config, isDark)
    }

    private fun requireColor(hex: String): Color =
        CheckoutTheme.parseColorOrNone(hex)
            ?: CheckoutTheme.parseColor(hex)
            ?: error("Invalid hex: $hex")

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonToMap(value)
                else -> value
            }
        }
        return map
    }

    private fun loadVectors(): JSONObject {
        val json = ThemeResolutionTest::class.java.classLoader
            ?.getResourceAsStream("test-vectors.json")
            ?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            ?: error("test-vectors.json not found in test resources")
        return JSONObject(json)
    }
}
