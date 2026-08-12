package com.xmoney.googlepay.internal

import android.app.Activity
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.WalletParams
import com.xmoney.payments.config.PaymentEnvironment
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class GooglePayHandler(activity: Activity, env: PaymentEnvironment) {
    private val client: PaymentsClient = Wallet.getPaymentsClient(
        activity,
        Wallet.WalletOptions.Builder()
            .setEnvironment(
                if (env.googlePayEnvironment == "PRODUCTION") WalletConstants.ENVIRONMENT_PRODUCTION
                else WalletConstants.ENVIRONMENT_TEST
            )
            .build()
    )

    suspend fun isReadyToPay(params: WalletParams): Boolean = suspendCancellableCoroutine { cont ->
        val request = IsReadyToPayRequest.fromJson(isReadyToPayRequest(params).toString())
        client.isReadyToPay(request).addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            val ready = if (task.isSuccessful) task.result == true else false
            cont.resume(ready)
        }
    }

    fun loadPaymentDataRequest(params: WalletParams, orderInfo: OrderPayloadInfo): PaymentDataRequest =
        PaymentDataRequest.fromJson(buildPaymentDataRequestJson(params, orderInfo).toString())

    val paymentsClient: PaymentsClient get() = client

    fun allowedPaymentMethodsJson(params: WalletParams): String =
        paymentDataRequest(params).getJSONArray("allowedPaymentMethods").toString()

    internal fun buildPaymentDataRequestJson(params: WalletParams, orderInfo: OrderPayloadInfo): JSONObject =
        paymentDataRequest(params).apply {
            put("merchantInfo", merchantInfoJson(params))
            put("transactionInfo", transactionInfoJson(params, orderInfo))
        }

    private fun isReadyToPayRequest(params: WalletParams): JSONObject =
        JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)
            .put("allowedPaymentMethods", JSONArray().put(cardPaymentMethodParameters(params)))

    private fun paymentDataRequest(params: WalletParams): JSONObject =
        JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)
            .put("allowedPaymentMethods", JSONArray().put(cardPaymentMethodWithTokenization(params)))

    private fun merchantInfoJson(params: WalletParams): JSONObject {
        val info = JSONObject().put("merchantName", params.merchantName ?: "Merchant")
        params.merchantId?.takeIf { it.isNotBlank() }?.let { info.put("merchantId", it) }
        return info
    }

    private fun transactionInfoJson(params: WalletParams, orderInfo: OrderPayloadInfo): JSONObject {
        val info = JSONObject()
            .put("totalPrice", formatPaymentAmount(orderInfo.amount))
            .put("totalPriceStatus", "FINAL")
            .put("currencyCode", orderInfo.currency ?: "EUR")
        params.merchantCountry?.takeIf { it.isNotBlank() }?.let { info.put("countryCode", it) }
        return info
    }

    private fun cardPaymentMethodParameters(params: WalletParams): JSONObject {
        val allowedNetworks = allowedNetworksJson(params)
        return JSONObject()
            .put("type", "CARD")
            .put(
                "parameters",
                JSONObject()
                    .put("allowedAuthMethods", JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS")))
                    .put("allowedCardNetworks", allowedNetworks)
            )
    }

    private fun cardPaymentMethodWithTokenization(params: WalletParams): JSONObject =
        cardPaymentMethodParameters(params).put(
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

    private fun allowedNetworksJson(params: WalletParams): JSONArray =
        JSONArray(
            (params.supportedNetworks.takeIf { it.isNotEmpty() } ?: listOf("VISA", "MASTERCARD"))
                .map { it.uppercase() }
        )

    companion object {
        fun extractToken(paymentData: PaymentData): String {
            val json = JSONObject(paymentData.toJson())
            return json
                .getJSONObject("paymentMethodData")
                .getJSONObject("tokenizationData")
                .getString("token")
        }

        fun formatPaymentAmount(amount: Double?): String =
            String.format(Locale.US, "%.2f", amount ?: 0.0)
    }
}
