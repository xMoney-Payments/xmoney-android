package com.xmoney.payments.service

import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.network.HttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentServiceCustomerDetailsTest {
    private val service = PaymentService(
        HttpClient.shared(),
        PaymentEnvironment.from("pk_test_x")!!,
        RuntimeEnvironment.getApplication(),
    )

    private val details = mapOf("firstName" to "Jane", "lastName" to "", "email" to "a@b.co")

    @Test
    fun cardFieldsIncludeCustomerDetailsJson() {
        val fields = service.cardFields(
            CardInput(number = "4242424242424242", expiryMonth = "12", expiryYear = "30", cvv = "123"),
            orderPayload = "payload",
            orderChecksum = "checksum",
            customerDetails = details,
        )
        val json = JSONObject(fields["customerDetails"])
        assertEquals("Jane", json.getString("firstName"))
        assertEquals("", json.getString("lastName"))
        assertEquals("a@b.co", json.getString("email"))
    }

    @Test
    fun savedCardAndWalletFieldsIncludeCustomerDetails() {
        val saved = service.savedCardFields("card_1", "payload", "checksum", details)
        val wallet = service.walletFields("googlePay", "token", "payload", "checksum", details)
        assertEquals(saved["customerDetails"], wallet["customerDetails"])
        assertEquals("a@b.co", JSONObject(saved["customerDetails"]).getString("email"))
    }

    @Test
    fun omitsCustomerDetailsWhenEmpty() {
        val fields = service.savedCardFields("card_1", "payload", "checksum", emptyMap())
        assertFalse(fields.containsKey("customerDetails"))
        val absent = service.walletFields("googlePay", "token", "payload", "checksum", null)
        assertFalse(absent.containsKey("customerDetails"))
    }
}
