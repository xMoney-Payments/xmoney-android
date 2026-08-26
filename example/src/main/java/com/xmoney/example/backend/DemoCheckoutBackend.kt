package com.xmoney.example.backend

import com.xmoney.example.BuildConfig
import com.xmoney.example.SAMPLE_AMOUNT_MINOR
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
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Demo-only checkout backend for the example app.
 *
 * Do **not** copy this into a merchant app. This client sends `API_KEY` because
 * the public demo server (`demo.xmoney.com`) is a stand-in for *your* backend.
 *
 * In production:
 * - The Android app holds only `publicKey`
 * - Your server creates the order and returns `payload` + `checksum`
 * - The app builds [PaymentIntent] from those two values
 */
object DemoCheckoutBackend {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun secretsError(): String? {
        if (BuildConfig.PUBLIC_KEY.isBlank() ||
            BuildConfig.PUBLIC_KEY.contains("replace", ignoreCase = true)
        ) {
            return "Set PUBLIC_KEY in example/secrets.properties"
        }
        if (BuildConfig.API_KEY.isBlank() ||
            BuildConfig.API_KEY.contains("your_api_key", ignoreCase = true)
        ) {
            return "Set API_KEY in example/secrets.properties"
        }
        return null
    }

    suspend fun createPaymentIntent(
        amountMinor: Long = SAMPLE_AMOUNT_MINOR,
        currency: String = BuildConfig.CURRENCY,
        description: String = BuildConfig.DESCRIPTION,
    ): PaymentIntent = withContext(Dispatchers.IO) {
        secretsError()?.let { throw IllegalStateException(it) }

        val amount = BigDecimal.valueOf(amountMinor).movePointLeft(2)
        val body = JSONObject()
            .put("amount", amount.toDouble())
            .put("currency", currency)
            .put("description", description)
            .put("publicKey", BuildConfig.PUBLIC_KEY)
            .put("apiKey", BuildConfig.API_KEY)
            .toString()

        val base = BuildConfig.API_BASE.trimEnd('/')
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
