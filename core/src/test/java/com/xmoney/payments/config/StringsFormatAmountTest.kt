package com.xmoney.payments.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StringsFormatAmountTest {
    @Test
    fun formatAmountUsesOptionsLocaleNotDeviceDefault() {
        val us = Strings.formatAmount(10.5, "EUR", "en-US")
        val el = Strings.formatAmount(10.5, "EUR", "el-GR")
        val ro = Strings.formatAmount(10.5, "EUR", "ro-RO")
        val bg = Strings.formatAmount(10.5, "EUR", "bg-BG")
        val hu = Strings.formatAmount(10.5, "EUR", "hu-HU")
        val pl = Strings.formatAmount(10.5, "EUR", "pl-PL")
        assertNotNull(us)
        assertNotNull(el)
        assertNotNull(ro)
        assertNotNull(bg)
        assertNotNull(hu)
        assertNotNull(pl)
        assertNotEquals(us, el)
        assertNotEquals(us, ro)
        assertNotEquals(us, bg)
        assertNotEquals(us, hu)
        assertNotEquals(us, pl)
    }

    @Test
    fun formatAmountFallsBackForBlankLocale() {
        val formatted = Strings.formatAmount(10.0, "EUR", "")
        assertNotNull(formatted)
        assertFalse(formatted!!.isEmpty())
    }

    @Test
    fun textResolvesLanguagePrefixToLocalizedCopy() {
        assertEquals("Płatność", Strings.text("sheet.title", "pl"))
        assertEquals("Płatność", Strings.text("sheet.title", "pl-PL"))
        assertEquals("Плащане", Strings.text("sheet.title", "bg"))
        assertEquals("Fizetés", Strings.text("sheet.title", "hu-HU"))
    }
}
