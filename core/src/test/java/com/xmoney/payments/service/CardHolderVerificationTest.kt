package com.xmoney.payments.service

import com.xmoney.payments.config.CardHolderName
import com.xmoney.payments.config.CardHolderVerification
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.AccountValidationResponse
import com.xmoney.payments.model.CardHolderMatchStatus
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.network.HttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardHolderVerificationTest {
    @Test
    fun buildValidationPayload_formatsExpiryAndName() {
        val payload = AccountService.buildValidationPayload(
            card = CardInput(
                number = "4111111111111111",
                expiryMonth = "12",
                expiryYear = "30",
                cvv = "123",
            ),
            name = CardHolderName(firstName = "John", middleName = "Q", lastName = "Doe"),
            currency = "EUR",
            transactionLocalDateTime = "2026-08-03T12:00:00Z",
        )

        @Suppress("UNCHECKED_CAST")
        val accountDetails = payload["accountDetails"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val account = accountDetails["account"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val name = accountDetails["name"] as Map<String, Any?>

        assertEquals("PAN", account["type"])
        assertEquals("4111111111111111", account["number"])
        assertEquals("2030-12", account["expiry"])
        assertEquals("123", account["cvc"])
        assertEquals("John", name["firstName"])
        assertEquals("Q", name["middleName"])
        assertEquals("Doe", name["lastName"])
        assertEquals("EUR", payload["currency"])
        assertEquals("2026-08-03T12:00:00Z", payload["transactionLocalDateTime"])
    }

    @Test
    fun buildValidationPayload_prefixesTwoDigitYear() {
        val payload = AccountService.buildValidationPayload(
            card = CardInput(number = "4111111111111111", expiryMonth = "1", expiryYear = "29", cvv = "999"),
            name = CardHolderName(firstName = "A", lastName = "B"),
            currency = "USD",
            transactionLocalDateTime = "t",
        )
        @Suppress("UNCHECKED_CAST")
        val account = (payload["accountDetails"] as Map<*, *>)["account"] as Map<*, *>
        assertEquals("2029-01", account["expiry"])
    }

    @Test
    fun nestedMapToJson_preservesStructure() {
        val payload = AccountService.buildValidationPayload(
            card = CardInput(number = "4111111111111111", expiryMonth = "08", expiryYear = "27", cvv = "100"),
            name = CardHolderName(firstName = "Ada", lastName = "Lovelace"),
            currency = "GBP",
            transactionLocalDateTime = "t",
        )
        val json = HttpClient.mapToJson(payload) as JSONObject
        val account = json.getJSONObject("accountDetails").getJSONObject("account")
        assertEquals("2027-08", account.getString("expiry"))
        assertEquals("Ada", json.getJSONObject("accountDetails").getJSONObject("name").getString("firstName"))
    }

    @Test
    fun parseVerificationResult_mapsStatuses() {
        val result = CardHolderVerificationResult.fromApiMap(
            mapOf(
                "status" to "PartialMatched",
                "firstNameStatus" to "Matched",
                "lastNameStatus" to "NotMatched",
            ),
        )
        assertEquals(CardHolderMatchStatus.PARTIAL_MATCHED, result.status)
        assertEquals(CardHolderMatchStatus.MATCHED, result.firstNameStatus)
        assertNull(result.middleNameStatus)
        assertEquals(CardHolderMatchStatus.NOT_MATCHED, result.lastNameStatus)
    }

    @Test
    fun parseVerificationResult_isCaseSensitive() {
        val result = CardHolderVerificationResult.fromApiMap(
            mapOf("status" to "MATCHED"),
        )
        assertEquals(CardHolderMatchStatus.NOT_VERIFIED, result.status)
    }

    @Test
    fun parseVerificationResult_defaultsWhenMissing() {
        val result = CardHolderVerificationResult.fromApiMap(null)
        assertEquals(CardHolderMatchStatus.NOT_VERIFIED, result.status)
    }

    @Test
    fun accountValidationResponse_parsesApiShape() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "networkResponseCode": "00",
                  "networkResponseCodeDescription": "Performed",
                  "nameValidationResults": {
                    "status": "Matched",
                    "firstNameStatus": "Matched",
                    "lastNameStatus": "Matched"
                  }
                }
                """.trimIndent(),
            ),
        )
        val response = AccountValidationResponse.fromApiMap(map)
        assertEquals("00", response.networkResponseCode)
        assertEquals("Performed", response.networkResponseCodeDescription)
        assertEquals(CardHolderMatchStatus.MATCHED, response.nameValidationResults.status)
        assertEquals(CardHolderMatchStatus.MATCHED, response.nameValidationResults.firstNameStatus)
        assertEquals(CardHolderMatchStatus.MATCHED, response.nameValidationResults.lastNameStatus)
        assertNull(response.nameValidationResults.middleNameStatus)
    }

    @Test
    fun cardHolderVerification_defaultsCallbackUntilHostReinjects() {
        val verification = CardHolderVerification(
            name = CardHolderName(firstName = "Jane", lastName = "Roe"),
        )
        assertEquals("Jane", verification.name.firstName)
        assertEquals("Roe", verification.name.lastName)
        assertFalse(
            verification.onCardHolderVerification(
                CardHolderVerificationResult(status = CardHolderMatchStatus.MATCHED),
            ),
        )
    }

    @Test
    fun resolvedConfig_preservesVerificationName() {
        val original = ResolvedPaymentConfig(
            publicKey = "test_pk",
            orderPayload = "p",
            orderChecksum = "c",
            card = CardConfig(
                cardHolderVerification = CardHolderVerification(
                    name = CardHolderName(firstName = "Jane", lastName = "Roe"),
                    onCardHolderVerification = { it.status == CardHolderMatchStatus.MATCHED },
                ),
            ),
        )
        val verification = original.card.cardHolderVerification
        assertNotNull(verification)
        assertEquals("Jane", verification!!.name.firstName)
        assertEquals("Roe", verification.name.lastName)
        assertTrue(
            verification.onCardHolderVerification(
                CardHolderVerificationResult(status = CardHolderMatchStatus.MATCHED),
            ),
        )
    }

    @Test
    fun cardConfig_omitsVerificationWhenNull() {
        val config = ResolvedPaymentConfig(
            publicKey = "test_pk",
            orderPayload = "p",
            orderChecksum = "c",
        )
        assertNull(config.card.cardHolderVerification)
    }
}
