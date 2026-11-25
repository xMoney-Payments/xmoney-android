package com.xmoney.paymentelement.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        assertEquals(theme.unselectedRing, theme.radioIdle)
        assertEquals(primary, theme.textBrand)
        assertEquals(primary, theme.iconBrand)
    }

    @Test
    fun omittedPrimaryKeepsBrandedWashes() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(Color(0xFFF7F6FF), theme.selectedBackground)
        assertEquals(Color(0xFFECE5FF), theme.accentIconBackground)
        assertEquals(Color(0xCC7C4DFF), theme.radioIdle)
        assertEquals(Color(0xFF7A4BFB), theme.textBrand)
        assertEquals(Color(0xFF7C4DFF), theme.iconBrand)
    }

    @Test
    fun secondaryTextOverridesSecondaryCopy() {
        val theme = resolveAppearance(
            mapOf("colors" to mapOf("secondaryText" to "#335577")),
            isDark = false,
        )
        val secondary = requireColor("#335577")
        assertEquals(secondary, theme.metaText)
        assertEquals(secondary, theme.sectionLabel)
    }

    @Test
    fun omittedSecondaryTextKeepsBrandedCopy() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(Color(0xFF63606D), theme.metaText)
        assertEquals(Color(0xFF6B6779), theme.sectionLabel)
    }

    @Test
    fun placeholderTextOverridesInputPlaceholder() {
        val light = resolveAppearance(
            mapOf("colors" to mapOf("placeholderText" to "#AA3300")),
            isDark = false,
        )
        assertEquals(requireColor("#AA3300"), light.inputPlaceholder)
        val dark = resolveAppearance(
            mapOf("colorsDark" to mapOf("placeholderText" to "#00AA33")),
            isDark = true,
        )
        assertEquals(requireColor("#00AA33"), dark.inputPlaceholder)

        assertEquals(Color(0xFF9691A3), resolveAppearance(emptyMap(), isDark = false).inputPlaceholder)
        assertEquals(Color(0xFF69696F), resolveAppearance(emptyMap(), isDark = true).inputPlaceholder)
    }

    @Test
    fun inverseTextContrastsWithPrimaryText() {
        assertEquals(Color(0xFFF7F6FF), resolveAppearance(emptyMap(), isDark = false).inverseText)
        assertEquals(Color(0xFF09090B), resolveAppearance(emptyMap(), isDark = true).inverseText)
        val custom = resolveAppearance(
            mapOf("colors" to mapOf("primaryText" to "#FFFFFF", "background" to "#FFFFFF")),
            isDark = false,
        )
        assertEquals(Color(0xFF16141A), custom.inverseText)
    }

    @Test
    fun dangerTintsUseErrorRedAtFixedAlphas() {
        val light = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(Color(0xFFEF4444).copy(alpha = 0.08f), light.dangerTint)
        assertEquals(Color(0xFFEF4444).copy(alpha = 0.09f), light.dangerTintStrong)
        val dark = resolveAppearance(emptyMap(), isDark = true)
        assertEquals(Color(0xFFEF4444).copy(alpha = 0.15f), dark.dangerTint)
        assertEquals(Color(0xFFEF4444).copy(alpha = 0.30f), dark.dangerTintStrong)
        val overridden = resolveAppearance(mapOf("colors" to mapOf("error" to "#00AA55")), isDark = false)
        assertEquals(light.dangerTint, overridden.dangerTint)
        assertEquals(Color(0xFF00AA55), overridden.errorBorder)
    }

    @Test
    fun groupedCardFieldsUseTwelvePointInset() {
        assertEquals(12.dp, com.xmoney.paymentelement.ui.CardFieldHorizontalPadding)
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
    fun buttonRadiusFollowsPrimaryButtonShape() {
        assertEquals(24f, resolveAppearance(emptyMap(), isDark = false).buttonRadius(48.dp).value, 0.001f)
        val square = resolveAppearance(
            mapOf("primaryButton" to mapOf("shapes" to mapOf("borderRadius" to 0))),
            isDark = false,
        )
        assertEquals(0f, square.buttonRadius(48.dp).value, 0.001f)
        val rounded = resolveAppearance(
            mapOf("primaryButton" to mapOf("shapes" to mapOf("borderRadius" to 8))),
            isDark = false,
        )
        assertEquals(8f, rounded.buttonRadius(52.dp).value, 0.001f)
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
        assertEquals(CheckoutTheme.DefaultPaymentContainerRadius, theme.paymentContainerRadius.value, 0.001f)
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
        assertEquals(Color(0xFFDC2626), theme.errorBorder)
        assertEquals(Color(0xFFDC2626), theme.errorText)
        assertEquals(Color(0xFFDC2626), theme.errorBadge)
    }

    @Test
    fun brandPrimaryIsXMoneyPurple() {
        assertEquals(Color(0xFF7C4DFF), CheckoutTheme.BrandPrimary)
    }

    @Test
    fun containerBorderDefaultsWhenOmitted() {
        val light = resolveAppearance(emptyMap(), isDark = false)
        assertEquals(Color.Transparent, light.containerBorder)

        val dark = resolveAppearance(emptyMap(), isDark = true)
        assertEquals(Color(0xFFF7F6F9).copy(alpha = 0x17 / 255f), dark.containerBorder)
    }

    @Test
    fun darkModeChromeUsesLightInk() {
        val theme = resolveAppearance(emptyMap(), isDark = true)
        val chrome = Color.White
        assertEquals(Color(0xFF18181B), theme.background)
        assertEquals(Color(0xFF1F1F23), theme.componentBackground)
        assertEquals(Color(0xFFFAFAFA), theme.primaryText)
        assertEquals(Color(0xFF69696F), theme.mutedIcon)
        assertEquals(chrome.copy(alpha = 0x33 / 255f), theme.unselectedRing)
        assertEquals(chrome.copy(alpha = 0x1A / 255f), theme.fieldBorder)
        assertEquals(chrome.copy(alpha = 0x1A / 255f), theme.fieldDivider)
        assertEquals(chrome.copy(alpha = 0x1A / 255f), theme.sheetHandle)
        assertEquals(chrome.copy(alpha = 0x1A / 255f), theme.orDivider)
        assertEquals(Color(0xFF2A2A2F), theme.selectedBackground)
        assertEquals(Color(0xFF24124C), theme.accentIconBackground)
        assertEquals(Color(0xFF926BFF), theme.textBrand)
        assertEquals(Color(0xFF605C70), theme.poweredByText)
        assertEquals(Color(0xFFEF4444), theme.errorText)
        assertEquals(Color(0xFFDC2626), theme.errorBadge)
        assertEquals(chrome.copy(alpha = 0x14 / 255f), theme.footerBorder)
    }

    @Test
    fun lightModeChromePreserved() {
        val theme = resolveAppearance(emptyMap(), isDark = false)
        val ink = Color(0xFF16141A)
        assertEquals(Color(0xFF9691A3), theme.mutedIcon)
        assertEquals(Color(0x2916141A), theme.unselectedRing)
        assertEquals(Color(0xFFE3E1E8), theme.fieldBorder)
        assertEquals(Color(0xFFE3E1E8), theme.fieldDivider)
        assertEquals(Color(0xFFE3E1E8), theme.sheetHandle)
        assertEquals(Color(0xFFE3E1E8), theme.orDivider)
        assertEquals(Color(0xFFF7F6FF), theme.background)
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
