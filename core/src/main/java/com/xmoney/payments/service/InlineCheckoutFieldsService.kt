package com.xmoney.payments.service

import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.CancellationException

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
class InlineCheckoutFieldsService(
    private val http: HttpClient,
    private val env: PaymentEnvironment,
) {
    /**
     * [InlineFieldsResult.unavailable] is true when the request cannot be made or fails.
     * A successful response may still list no fields.
     */
    suspend fun getFields(sessionToken: String): InlineFieldsResult {
        if (sessionToken.isBlank()) return InlineFieldsResult(emptyList(), unavailable = true)
        return try {
            val url = ApiUrl.make(env.apiNextBaseURL, SdkConstants.INLINE_CHECKOUT_FIELDS_PATH)
            val result = http.getJson(url, sessionToken)
            val fields = (result["data"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            InlineFieldsResult(fields, unavailable = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            InlineFieldsResult(emptyList(), unavailable = true)
        }
    }
}

data class InlineFieldsResult(
    val fields: List<String>,
    val unavailable: Boolean,
)
