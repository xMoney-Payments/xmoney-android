package com.xmoney.payments.threeds

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreeDSFormBodyTest {
    @Test
    fun encodeBuildsFormUrlEncodedBody() {
        val body = ThreeDSFormBody.encode(
            mapOf(
                "PaReq" to "a+b",
                "MD" to "md 1",
            ),
        )
        assertEquals("PaReq=a%2Bb&MD=md+1", String(body, Charsets.UTF_8))
    }
}
