package com.xmoney.payments.model

import com.xmoney.payments.network.HttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResponseModelsTest {
    @Test
    fun sessionTokenResponse_parsesToken() {
        val map = HttpClient.jsonToMap(JSONObject("""{"token":"abc-123"}"""))
        assertEquals("abc-123", SessionTokenResponse.fromApiMap(map).token)
    }

    @Test
    fun siteConfig_parsesFlags() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "whitelabelPaymentForm": false,
                  "checkNameWithoutSaveCard": true,
                  "nameCheckValidationEnabled": true
                }
                """.trimIndent(),
            ),
        )
        val config = SiteConfig.fromApiMap(map)
        assertFalse(config.whitelabelPaymentForm)
        assertTrue(config.checkNameWithoutSaveCard)
        assertTrue(config.nameCheckValidationEnabled)
    }

    @Test
    fun savedCardsResponse_parsesApiShape() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "data": [
                    {
                      "id": 141973,
                      "customerId": 62833,
                      "type": "mastercard",
                      "cardNumber": "555555******5599",
                      "expiryMonth": "12",
                      "expiryYear": "2034",
                      "nameOnCard": "Minas Kitsos",
                      "cardHolderCountry": "RO",
                      "bankName": ""
                    },
                    {
                      "id": 143072,
                      "customerId": 62833,
                      "type": "visa",
                      "cardNumber": "411111******1111",
                      "expiryMonth": "12",
                      "expiryYear": "2028",
                      "nameOnCard": "TestCata",
                      "cardHolderCountry": null,
                      "bankName": ""
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val cards = SavedCardsResponse.fromApiMap(map).data
        assertEquals(2, cards.size)

        val first = cards[0]
        assertEquals("141973", first.id)
        assertEquals("62833", first.customerId)
        assertEquals("mastercard", first.cardType)
        assertEquals("mastercard", first.cardBrand)
        assertEquals("555555******5599", first.cardNumber)
        assertEquals("12/34", first.cardExpiryDate)
        assertEquals("Minas Kitsos", first.nameOnCard)
        assertEquals("RO", first.cardHolderCountry)
        assertNull(first.issuerName)

        val second = cards[1]
        assertEquals("143072", second.id)
        assertEquals("visa", second.cardBrand)
        assertEquals("12/28", second.cardExpiryDate)
        assertNull(second.cardHolderCountry)
    }

    @Test
    fun walletParams_parsesAllowedCardNetworks() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "allowedCardNetworks": ["MASTERCARD", "VISA"],
                  "gateway": "xmoneypay",
                  "gatewayMerchantId": "googlePay_10722",
                  "merchantName": "TestxMoney",
                  "merchantCountry": "RO",
                  "merchantId": "googlePay_10722",
                  "merchantOrigin": ""
                }
                """.trimIndent(),
            ),
        )
        val params = WalletParams.fromApiMap(map)
        assertEquals("xmoneypay", params.gateway)
        assertEquals("googlePay_10722", params.gatewayMerchantId)
        assertEquals("googlePay_10722", params.merchantId)
        assertEquals("TestxMoney", params.merchantName)
        assertEquals("RO", params.merchantCountry)
        assertNull(params.merchantOrigin)
        assertEquals(listOf("MASTERCARD", "VISA"), params.supportedNetworks)
    }

    @Test
    fun transaction_parsesFullApiShape() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "id": 726549,
                  "transactionStatus": "complete-ok",
                  "amount": "100.0000",
                  "currencyKey": "EUR",
                  "amountInEuro": "100.0000",
                  "customerData": {
                    "id": 62833,
                    "siteId": 10722,
                    "identifier": "customer-12333",
                    "firstName": "John",
                    "lastName": "Doe",
                    "country": "RO",
                    "state": "",
                    "city": "Bucharest",
                    "zipCode": "",
                    "address": "",
                    "phone": "",
                    "email": "john.doe@test.com",
                    "isWhitelisted": 0,
                    "isWhitelistedUntil": null,
                    "creationDate": "2025-12-14T16:40:00+00:00",
                    "creationTimestamp": 1765730400
                  },
                  "externalOrderId": "order-1786545845128",
                  "description": "Embeddable Configuration - Payment Card"
                }
                """.trimIndent(),
            ),
        )
        val tx = Transaction.fromApiMap(map)
        assertEquals("726549", tx.id)
        assertEquals("complete-ok", tx.status)
        assertEquals("100.0000", tx.amount)
        assertEquals("EUR", tx.currencyKey)
        assertEquals("100.0000", tx.amountInEuro)
        assertEquals("order-1786545845128", tx.externalOrderId)
        assertEquals("Embeddable Configuration - Payment Card", tx.description)
        assertTrue(tx.isComplete)
        assertTrue(tx.isSuccessfulComplete)

        val customer = tx.customerData!!
        assertEquals("62833", customer.id)
        assertEquals("10722", customer.siteId)
        assertEquals("customer-12333", customer.identifier)
        assertEquals("John", customer.firstName)
        assertEquals("Doe", customer.lastName)
        assertEquals("RO", customer.country)
        assertNull(customer.state)
        assertEquals("Bucharest", customer.city)
        assertEquals("john.doe@test.com", customer.email)
        assertFalse(customer.isWhitelisted)
        assertNull(customer.isWhitelistedUntil)
        assertEquals("2025-12-14T16:40:00+00:00", customer.creationDate)
        assertEquals(1765730400L, customer.creationTimestamp)
    }

    @Test
    fun orderInput_parsesDecodedPayload() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "publicKey": "pk_test_123",
                  "cardTransactionMode": "authAndCapture",
                  "invoiceEmail": "merchant@test.com",
                  "saveCard": true,
                  "cardId": 141973,
                  "backUrl": "https://merchant.example/return",
                  "customData": "{\"foo\":1}",
                  "customer": {
                    "identifier": "customer-12333",
                    "firstName": "John",
                    "lastName": "Doe",
                    "country": "RO",
                    "city": "Bucharest",
                    "phone": "",
                    "email": "john.doe@test.com",
                    "tags": ["vip", "test"]
                  },
                  "order": {
                    "orderId": "order-1786545845128",
                    "type": "purchase",
                    "amount": 100,
                    "currency": "EUR",
                    "description": "Embeddable Configuration - Payment Card",
                    "intervalType": "month",
                    "intervalValue": "1",
                    "retryPayment": "true",
                    "trialAmount": 0,
                    "firstBillDate": "2026-09-01"
                  }
                }
                """.trimIndent(),
            ),
        )
        val input = OrderInput.fromApiMap(map)
        assertEquals("pk_test_123", input.publicKey)
        assertEquals("authAndCapture", input.cardTransactionMode)
        assertEquals("merchant@test.com", input.invoiceEmail)
        assertTrue(input.saveCard)
        assertEquals("141973", input.cardId)
        assertEquals("https://merchant.example/return", input.backUrl)
        assertEquals("{\"foo\":1}", input.customData)

        val customer = input.customer!!
        assertEquals("customer-12333", customer.identifier)
        assertEquals("John", customer.firstName)
        assertEquals("Doe", customer.lastName)
        assertEquals("RO", customer.country)
        assertEquals("Bucharest", customer.city)
        assertNull(customer.phone)
        assertEquals("john.doe@test.com", customer.email)
        assertEquals(listOf("vip", "test"), customer.tags)

        val order = input.order!!
        assertEquals("order-1786545845128", order.orderId)
        assertEquals("purchase", order.type)
        assertEquals(100.0, order.amount!!, 0.0)
        assertEquals("EUR", order.currency)
        assertEquals("Embeddable Configuration - Payment Card", order.description)
        assertEquals("month", order.intervalType)
        assertEquals("1", order.intervalValue)
        assertEquals("true", order.retryPayment)
        assertEquals(0.0, order.trialAmount!!, 0.0)
        assertEquals("2026-09-01", order.firstBillDate)

        val info = input.toInfo()
        assertEquals("authAndCapture", info.cardTransactionMode)
        assertFalse(info.isVerifyCard)
        assertEquals(100.0, info.amount!!, 0.0)
        assertEquals("EUR", info.currency)
        assertEquals("order-1786545845128", info.externalOrderId)
        assertFalse(info.isRecurring)
    }
}
