package com.xmoney.payments.model

import com.xmoney.payments.config.Appearance
import com.xmoney.payments.config.Strings
import com.xmoney.payments.validation.CardFieldValidators

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ContractTest {
    private val vectors: JSONObject = loadVectors()

    @Test
    fun validationVectors() {
        val cases = vectors.getJSONArray("validation")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val field = case.getString("field")
            val expectedKey = when {
                case.isNull("expectedKey") -> null
                else -> case.getString("expectedKey")
            }
            val error = when (field) {
                "cardNumber" -> CardFieldValidators.validateCardNumber(case.getString("input"))
                "expiry" -> {
                    val input = case.getJSONObject("input")
                    CardFieldValidators.validateExpiry(
                        input.optString("month", ""),
                        input.optString("year", ""),
                    )
                }
                "cvv" -> CardFieldValidators.validateCVV(case.getString("input"))
                "holderName" -> CardFieldValidators.validateHolderName(case.getString("input"))
                else -> throw IllegalArgumentException("Unknown field: $field")
            }
            if (expectedKey == null) {
                assertNull("Expected no error for $field case $i", error)
            } else {
                assertEquals(
                    "Mismatch for $field case $i",
                    expectedKey,
                    error?.messageKey,
                )
            }
        }
    }

    @Test
    fun buttonTypeLabels() {
        val cases = vectors.getJSONArray("buttonTypes")
        val sampleAmount = "€1.00"
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val type = case.getString("type")
            val expectedKey = case.getString("key")
            val expected = Strings.text(expectedKey, "en-US", mapOf("amount" to sampleAmount))
            val actual = Strings.submitButtonTitle(type, "en-US", sampleAmount)
            assertEquals("Button type $type", expected, actual)
        }
    }

    @Test
    fun orderPayloadVectors() {
        val cases = vectors.getJSONArray("orderPayload")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val payload = case.getString("payload")
            val expected = case.getJSONObject("expected")
            val info = OrderPayloadDecoder.info(payload)
            assertEquals("amount case $i", expected.getDouble("amount"), info.amount ?: 0.0, 0.001)
            assertEquals("currency case $i", expected.getString("currency"), info.currency)
            assertEquals("externalOrderId case $i", expected.getString("externalOrderId"), info.externalOrderId)
            assertEquals("isRecurring case $i", expected.getBoolean("isRecurring"), info.isRecurring)
            assertEquals("isVerifyCard case $i", expected.getBoolean("isVerifyCard"), info.isVerifyCard)
            assertEquals(
                "cardTransactionMode case $i",
                expected.getString("cardTransactionMode"),
                info.cardTransactionMode,
            )
        }
    }

    @Test
    fun errorCodeTaxonomy() {
        val cases = vectors.getJSONArray("errorCodes")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val code = case.getString("code")
            val message = case.getString("message")
            val error = PaymentError.from(code, message)
            assertEquals("code case $i", code, error.code)
            assertEquals("message case $i", message, error.message)
        }
    }

    @Test
    fun themeResolutionVectors() {
        val cases = vectors.getJSONArray("themeResolution")
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val item = cases.getJSONObject(i)
            val sent = item.getJSONObject("appearance")
            val appearance = Appearance.from(jsonToMap(sent))
            val expected = item.getJSONObject("expected")
            val isDark = item.getBoolean("isDark")
            if (sent.has("brandColor")) {
                assertEquals(
                    expected.getString("primary"),
                    appearance.brandColor!!.srgb(isDark).hex.lowercase(),
                )
            } else {
                assertEquals(null, appearance.brandColor)
            }
            if (sent.has("backgroundColor")) {
                assertEquals(
                    expected.getString("background"),
                    appearance.backgroundColor!!.srgb(isDark).hex.lowercase(),
                )
            } else {
                assertEquals(null, appearance.backgroundColor)
            }
        }
    }

    @Test
    fun localeVectors() {
        val cases = vectors.getJSONArray("locales")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val locale = case.getString("locale")
            val key = case.getString("key")
            val contains = case.getString("contains")
            val text = Strings.text(key, locale)
            assertTrue("locale $locale case $i", text.contains(contains))
        }
    }

    private fun jsonToMap(source: JSONObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = source.get(key)
            map[key] = if (value is JSONObject) jsonToMap(value) else value
        }
        return map
    }

    private fun loadVectors(): JSONObject {
        val json = ContractTest::class.java.classLoader
            ?.getResourceAsStream("test-vectors.json")
            ?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            ?: error("test-vectors.json not found in test resources")
        return JSONObject(json)
    }
}
