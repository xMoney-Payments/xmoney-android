package com.xmoney.payments.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentErrorMerchantMessageTest {
    @Test
    fun networkAlwaysUsesGenericMessage() {
        val error = PaymentError.Network("secret server dump")
        assertEquals(PaymentError.GENERIC_NETWORK, error.merchantMessage())
        assertEquals(PaymentError.GENERIC_NETWORK, PaymentResult.failed(error).errorMessage)
    }

    @Test
    fun unknownServerCodeIsSanitized() {
        val error = PaymentError.Unknown("E_INTERNAL", "stack trace from edge")
        assertEquals(PaymentError.GENERIC_REQUEST, error.merchantMessage())
    }

    @Test
    fun sdkAuthoredMessagesPassThrough() {
        val error = PaymentError.Session()
        assertEquals("Missing session token", error.merchantMessage())
    }
}
