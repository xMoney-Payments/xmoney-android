package com.xmoney.payments.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorMathTest {
    @Test
    fun yellowOnCreamStopsNearTheContrastFloor() {
        val yellow = SrgbColor.fromHex("#FACC15")!!
        val cream = SrgbColor.fromHex("#F7F6FF")!!
        val adjusted = yellow.withContrast(4.5, cream)
        val ratio = adjusted.contrast(cream)
        assertTrue(ratio >= 4.5)
        assertTrue(ratio < 5.2)
    }

    @Test
    fun unreachableTargetFallsBackToInkOrPaper() {
        val gray = SrgbColor.fromHex("#777777")!!
        val adjusted = gray.withContrast(7.0, gray)
        val ink = SrgbColor.INK.contrast(gray)
        val paper = SrgbColor.PAPER.contrast(gray)
        val expected = if (ink >= paper) SrgbColor.INK else SrgbColor.PAPER
        assertEquals(expected.red, adjusted.red, 0.001)
        assertEquals(expected.green, adjusted.green, 0.001)
        assertEquals(expected.blue, adjusted.blue, 0.001)
    }

    @Test
    fun translucentContrastUsesThePaintedColor() {
        val veil = SrgbColor.WHITE.withAlpha(0.2)
        val painted = veil.contrast(SrgbColor.BLACK, compositedOver = SrgbColor.BLACK)
        val opaque = SrgbColor.WHITE.contrast(SrgbColor.BLACK)
        assertTrue(painted > 1.0)
        assertTrue(painted < opaque)
        assertEquals(veil.composited(SrgbColor.BLACK).contrast(SrgbColor.BLACK), painted, 0.001)
    }
}
