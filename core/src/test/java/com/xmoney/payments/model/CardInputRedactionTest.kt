package com.xmoney.payments.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardInputRedactionTest {
    @Test
    fun toString_doesNotIncludePanOrCvv() {
        val input = CardInput(
            number = "4111111111111111",
            expiryMonth = "12",
            expiryYear = "30",
            cvv = "123",
            holderName = "Ada Lovelace",
        )
        val text = input.toString()
        assertFalse(text.contains("4111111111111111"))
        assertFalse(text.contains("123"))
        assertFalse(text.contains("Ada"))
        assertTrue(text.contains("****"))
        assertTrue(text.contains("cvv=***"))
    }
}
