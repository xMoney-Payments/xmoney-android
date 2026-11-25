package com.xmoney.paymentsheet

import com.xmoney.paymentsheet.internal.sheetLiftShadow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetLiftShadowTest {
    @Test
    fun bitmapHeightDependsOnWidthNotOnATallSheet() {
        val density = 3f
        val shadow = sheetLiftShadow(width = 1080, density = density)
        requireNotNull(shadow)
        val lift = 24f * density
        val blur = 35f * density
        val radius = 32f * density
        val expected = (lift + blur * 2f + radius).toInt() + 4
        assertEquals(1080, shadow.width)
        assertEquals(expected, shadow.height)
        assertTrue(shadow.height < 1080)
    }
}
