package com.xmoney.googlepay.internal

import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.WalletParams
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePayHandlerTest {
    private val params = WalletParams(
        gateway = "example",
        gatewayMerchantId = "exampleGatewayMerchantId",
        merchantId = "BCR2DN4T57G5Z6HF",
        merchantName = "Test Merchant",
        merchantCountry = "RO",
        supportedNetworks = listOf("visa", "mastercard"),
    )

    private val orderInfo = OrderPayloadInfo(
        cardTransactionMode = null,
        isVerifyCard = false,
        amount = 100.0,
        currency = "EUR",
        externalOrderId = null,
        isRecurring = false,
    )

    @Test
    fun allowedPaymentMethodsJson_includesTokenization() {
        val json = JSONArray(GooglePayHandlerTestHelper.allowedPaymentMethodsJson(params))
        val method = json.getJSONObject(0)
        assertTrue(method.has("tokenizationSpecification"))
    }

    @Test
    fun isReadyToPayRequest_omitsTokenization() {
        val root = JSONObject(GooglePayHandlerTestHelper.isReadyToPayRequestJson(params))
        val method = root.getJSONArray("allowedPaymentMethods").getJSONObject(0)
        assertFalse(method.has("tokenizationSpecification"))
        assertTrue(method.has("parameters"))
    }

    @Test
    fun paymentDataRequest_includesMerchantIdAndCountryCode() {
        val request = GooglePayHandlerTestHelper.paymentDataRequestJson(params, orderInfo)
        assertEquals("BCR2DN4T57G5Z6HF", request.getJSONObject("merchantInfo").getString("merchantId"))
        assertEquals("RO", request.getJSONObject("transactionInfo").getString("countryCode"))
        assertEquals("100.00", request.getJSONObject("transactionInfo").getString("totalPrice"))
    }

    @Test
    fun formatPaymentAmount_usesTwoDecimalPlaces() {
        assertEquals("19.99", GooglePayHandler.formatPaymentAmount(19.99))
        assertEquals("100.00", GooglePayHandler.formatPaymentAmount(100.0))
    }
}

internal object GooglePayHandlerTestHelper {
    fun allowedPaymentMethodsJson(params: WalletParams): String {
        val networks = JSONArray(params.supportedNetworks.map { it.uppercase() })
        val method = JSONObject()
            .put("type", "CARD")
            .put(
                "parameters",
                JSONObject()
                    .put("allowedAuthMethods", JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS")))
                    .put("allowedCardNetworks", networks)
            )
            .put(
                "tokenizationSpecification",
                JSONObject()
                    .put("type", "PAYMENT_GATEWAY")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("gateway", params.gateway ?: "")
                            .put("gatewayMerchantId", params.gatewayMerchantId ?: "")
                    )
            )
        return JSONArray().put(method).toString()
    }

    fun isReadyToPayRequestJson(params: WalletParams): String {
        val networks = JSONArray(params.supportedNetworks.map { it.uppercase() })
        val method = JSONObject()
            .put("type", "CARD")
            .put(
                "parameters",
                JSONObject()
                    .put("allowedAuthMethods", JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS")))
                    .put("allowedCardNetworks", networks)
            )
        return JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)
            .put("allowedPaymentMethods", JSONArray().put(method))
            .toString()
    }

    fun paymentDataRequestJson(params: WalletParams, orderInfo: OrderPayloadInfo): JSONObject {
        val merchantInfo = JSONObject().put("merchantName", params.merchantName ?: "Merchant")
        params.merchantId?.takeIf { it.isNotBlank() }?.let { merchantInfo.put("merchantId", it) }

        val transactionInfo = JSONObject()
            .put("totalPrice", GooglePayHandler.formatPaymentAmount(orderInfo.amount))
            .put("totalPriceStatus", "FINAL")
            .put("currencyCode", orderInfo.currency ?: "EUR")
        params.merchantCountry?.takeIf { it.isNotBlank() }?.let { transactionInfo.put("countryCode", it) }

        return JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)
            .put("allowedPaymentMethods", JSONArray(allowedPaymentMethodsJson(params)))
            .put("merchantInfo", merchantInfo)
            .put("transactionInfo", transactionInfo)
    }
}
