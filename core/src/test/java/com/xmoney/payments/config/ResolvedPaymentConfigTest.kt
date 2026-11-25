package com.xmoney.payments.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedPaymentConfigTest {
    @Test
    fun toPaymentConfigKeepsCustomerDetails() {
        val resolved = ResolvedPaymentConfig(
            publicKey = "pk_test",
            orderPayload = "payload",
            orderChecksum = "checksum",
            customerDetails = CustomerDetailsConfig(card = true, googlePay = true),
        )
        val config = resolved.toPaymentConfig()
        assertTrue(config.customerDetails.card)
        assertTrue(config.customerDetails.googlePay)
        assertEquals("pk_test", config.publicKey)
    }
}
