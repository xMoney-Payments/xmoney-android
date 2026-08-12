package com.xmoney.payments.threeds

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeDSUrlAllowlistTest {
    @Test
    fun allowsHttpsAndAboutBlank() {
        assertTrue(ThreeDSUrlAllowlist.isAllowed("https://acs.example.com/challenge"))
        assertTrue(ThreeDSUrlAllowlist.isAllowed("about:blank"))
    }

    @Test
    fun blocksUnsafeSchemes() {
        assertFalse(ThreeDSUrlAllowlist.isAllowed("http://acs.example.com/challenge"))
        assertFalse(ThreeDSUrlAllowlist.isAllowed("file:///sdcard/x.html"))
        assertFalse(ThreeDSUrlAllowlist.isAllowed("javascript:alert(1)"))
        assertFalse(ThreeDSUrlAllowlist.isAllowed("content://com.example/x"))
    }
}
