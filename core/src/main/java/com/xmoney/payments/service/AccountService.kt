package com.xmoney.payments.service

import com.xmoney.payments.config.CardHolderName
import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.AccountValidationResponse
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.SessionTokenResponse
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class AccountService(private val http: HttpClient, private val env: PaymentEnvironment) {
    suspend fun getSessionToken(orderPayload: String, orderChecksum: String): String {
        val url = ApiUrl.make(env.apiNextBaseURL, SdkConstants.SESSION_TOKEN_PATH)
        val result = http.postJson(url, mapOf("payload" to orderPayload, "checksum" to orderChecksum))
        return SessionTokenResponse.fromApiMap(result).token
            ?: throw PaymentError.Session()
    }

    suspend fun validateAccount(
        card: CardInput,
        name: CardHolderName,
        currency: String,
        sessionToken: String,
    ): CardHolderVerificationResult {
        val payload = buildValidationPayload(
            card = card,
            name = name,
            currency = currency,
            transactionLocalDateTime = isoNow(),
        )
        val url = ApiUrl.make(env.apiNextBaseURL, SdkConstants.ACCOUNT_VALIDATION_PATH)
        val result = http.postJson(url, payload, bearer = sessionToken)
        return AccountValidationResponse.fromApiMap(result).nameValidationResults
    }

    companion object {
        fun buildValidationPayload(
            card: CardInput,
            name: CardHolderName,
            currency: String,
            transactionLocalDateTime: String,
        ): Map<String, Any?> {
            val year = card.expiryYear.trim().let { y ->
                when {
                    y.length == 2 -> "20$y"
                    else -> y
                }
            }
            val month = card.expiryMonth.trim().padStart(2, '0')
            return mapOf(
                "accountDetails" to mapOf(
                    "account" to mapOf(
                        "type" to "PAN",
                        "number" to card.number,
                        "expiry" to "$year-$month",
                        "cvc" to card.cvv,
                    ),
                    "name" to name.toMap(),
                ),
                "currency" to currency,
                "transactionLocalDateTime" to transactionLocalDateTime,
            )
        }

        fun isoNow(): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(Date())
        }
    }
}
