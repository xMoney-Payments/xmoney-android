package com.xmoney.example

import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderCredentials
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CheckoutApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun createPaymentIntent(
        apiBase: String,
        publicKey: String,
        apiKey: String,
        currency: String,
        description: String,
        amount: Double = 100.0,
    ): PaymentIntent = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("amount", amount)
            .put("currency", currency)
            .put("description", description)
            .put("publicKey", publicKey)
            .put("apiKey", apiKey)
            .toString()

        val base = apiBase.trimEnd('/')
        val request = Request.Builder()
            .url("$base/api/orders")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse {
                throw IllegalStateException("Invalid response from checkout API")
            }
            if (!response.isSuccessful) {
                val message = json.optString("error").ifBlank {
                    json.optString("message").ifBlank { "HTTP ${response.code}" }
                }
                throw IllegalStateException(message)
            }
            val payload = json.optString("payload")
            val checksum = json.optString("checksum")
            if (payload.isBlank() || checksum.isBlank()) {
                throw IllegalStateException("Missing payload or checksum in API response")
            }
            PaymentIntent(
                OrderCredentials(
                    orderPayload = OrderPayload(payload),
                    orderChecksum = OrderChecksum(checksum),
                ),
            )
        }
    }
}
