package com.xmoney.payments.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class OrderPayloadReturnURLTest {
    @Test
    fun matchesSchemeHostAndPathPrefix() {
        val back = URI("https://merchant.example/pay/return")
        assertTrue(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/pay/return"),
                back,
            ),
        )
        assertTrue(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/pay/return?status=ok"),
                back,
            ),
        )
        assertTrue(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/pay/return/extra"),
                back,
            ),
        )
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                URI("http://merchant.example/pay/return"),
                back,
            ),
        )
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://evil.example/pay/return"),
                back,
            ),
        )
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/other"),
                back,
            ),
        )
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/pay/returnx"),
                back,
            ),
        )
    }

    @Test
    fun rootBackUrlDoesNotMatchChallengePaths() {
        val back = URI("https://merchant.example/")
        assertTrue(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/"),
                back,
            ),
        )
        assertTrue(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/?status=ok"),
                back,
            ),
        )
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/threeds/challenge"),
                back,
            ),
        )
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                URI("https://merchant.example/pay/return"),
                back,
            ),
        )
    }

    @Test
    fun queryStatusIsNotEnoughWithoutBackURL() {
        assertNull(OrderPayloadDecoder.backUrl("e30="))
        assertFalse(
            OrderPayloadDecoder.matchesReturnURL(
                "https://evil.example/?status=ok",
                URI("https://merchant.example/pay/return"),
            ),
        )
    }
}
