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
     * Returns an empty list when the request cannot be made or fails.
     * An empty list turns collection off and the payment still proceeds.
     */
    suspend fun getFields(sessionToken: String): List<String> {
        if (sessionToken.isBlank()) return emptyList()
        return try {
            val url = ApiUrl.make(env.apiNextBaseURL, SdkConstants.INLINE_CHECKOUT_FIELDS_PATH)
            val result = http.getJson(url, sessionToken)
            (result["data"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
