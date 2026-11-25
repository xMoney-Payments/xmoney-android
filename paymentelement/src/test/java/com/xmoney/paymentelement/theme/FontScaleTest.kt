package com.xmoney.paymentelement.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontScaleTest {
    @Test
    fun sixteenSpAtFontScaleTwoIsThirtyTwoPixels() {
        val theme = theme(contentSizeScale = 2f)
        assertEquals(2f, theme.fontScale, 0.001f)
        assertEquals(96f, theme.formFieldHeight.value, 0.01f)
        val density = Density(density = 1f, fontScale = 2f)
        val pixels = with(density) { theme.scaledSp(16f).toPx() }
        val once = with(density) { 16.sp.toPx() }
        assertEquals(once, pixels, 0.5f)
        assertTrue(pixels < 48f)
    }

    @Test
    fun sixteenSpAtFontScaleOnePointThreeIsNotDoubled() {
        val theme = theme(contentSizeScale = 1.3f)
        assertEquals(1.3f, theme.fontScale, 0.001f)
        assertEquals(62.4f, theme.formFieldHeight.value, 0.01f)
        val density = Density(density = 1f, fontScale = 1.3f)
        val pixels = with(density) { theme.scaledSp(16f).toPx() }
        val once = with(density) { 16.sp.toPx() }
        assertEquals(once, pixels, 0.5f)
        assertTrue(pixels < 28f)
    }

    private fun theme(contentSizeScale: Float): CheckoutTheme {
        val config = ResolvedPaymentConfig(
            publicKey = "pk_test",
            orderPayload = "payload",
            orderChecksum = "checksum",
            options = OptionsConfig(),
        )
        return CheckoutTheme.resolve(config, isDark = false, contentSizeScale = contentSizeScale)
    }
}
