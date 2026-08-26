package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.ConfirmPaymentResponse
import com.xmoney.payments.model.ConfirmTransaction
import com.xmoney.payments.model.PaymentSubmissionResult
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient
import com.xmoney.payments.util.DeviceMetadata
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.payments.threeds.ThreeDSUrlAllowlist

import android.content.Context
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class PaymentService(
    private val http: HttpClient,
    private val env: PaymentEnvironment,
    private val context: Context,
) {
    fun cardFields(card: CardInput, orderPayload: String, orderChecksum: String): Map<String, String> {
        val fields = HashMap<String, String>()
        fields["cardNumber"] = CardFieldValidators.normalizeDigits(card.number)
        fields["cardExpiryMonth"] = card.expiryMonth
        fields["cardExpiryYear"] = card.expiryYear
        fields["cardCvv"] = card.cvv
        fields["jsonRequest"] = orderPayload
        fields["checksum"] = orderChecksum
        if (card.saveCard) fields["saveCard"] = "true"
        card.holderName?.takeIf { it.isNotEmpty() }?.let { fields["cardHolderName"] = it }
        fields.putAll(DeviceMetadata.fields(context))
        return fields
    }

    fun savedCardFields(cardId: String, orderPayload: String, orderChecksum: String): Map<String, String> {
        val fields = HashMap<String, String>()
        fields["jsonRequest"] = orderPayload
        fields["checksum"] = orderChecksum
        fields["cardId"] = cardId
        fields.putAll(DeviceMetadata.fields(context))
        return fields
    }

    fun walletFields(
        walletType: String,
        token: String,
        orderPayload: String,
        orderChecksum: String,
    ): Map<String, String> {
        val fields = HashMap<String, String>()
        fields["jsonRequest"] = orderPayload
        fields["checksum"] = orderChecksum
        fields["digitalWalletType"] = walletType
        fields["digitalWalletData"] = token
        fields.putAll(DeviceMetadata.fields(context))
        return fields
    }

    suspend fun confirmPayment(fields: Map<String, String>): ConfirmPaymentResponse {
        val url = ApiUrl.make(env.secureBaseURL, SdkConstants.CONFIRM_PAYMENT_PATH)
        return ConfirmPaymentResponse.fromApiMap(http.postMultipart(url, fields))
    }
    @androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

    data class ParsedResponse(val submission: PaymentSubmissionResult, val transactionId: String?)

    fun parse(result: ConfirmPaymentResponse): ParsedResponse {
        val data = result.data
        val transaction = data?.transaction
        val checkoutThreeDS = isCheckoutThreeDS(transaction)
        val challengeUrl = listOf(
            data?.threeDSFlowUrl,
            if (!checkoutThreeDS) data?.redirectUrl else null,
            transaction?.redirectUrl,
        ).firstOrNull { !it.isNullOrBlank() && ThreeDSUrlAllowlist.isHttpsChallenge(it) }
        val requiresThreeDS = checkoutThreeDS || challengeUrl != null || (
            data != null && (data.is3d || data.isRedirect)
            )
        if (data == null || !isSuccessCode(result.code, requiresThreeDS)) {
            val message = result.status?.let { "Payment error: $it" }
                ?: "Invalid payment result"
            throw PaymentError.Payment(message)
        }

        val transactionId = transaction?.transactionId ?: data.transactionId
        val responseStatus = transaction?.responseStatus

        if (requiresThreeDS) {
            if (challengeUrl != null) {
                val useRedirect = !checkoutThreeDS && challengeUrl == data.redirectUrl
                val submission = if (useRedirect) {
                    PaymentSubmissionResult.Needs3DS(
                        challengeUrl,
                        data.redirectFormMethod,
                        data.redirectParams,
                    )
                } else {
                    PaymentSubmissionResult.Needs3DS(challengeUrl)
                }
                return ParsedResponse(submission, transactionId)
            }
            throw PaymentError.ThreeDS()
        }

        val backUrl = data.orderRequestBackUrl
        if (backUrl != null) {
            val builder = StringBuilder(backUrl)
            builder.append(if (backUrl.contains("?")) "&" else "?")
            data.result?.let { builder.append("result=").append(it).append("&") }
            responseStatus?.let { builder.append("status=").append(it) }
            return ParsedResponse(PaymentSubmissionResult.Redirect(builder.toString()), transactionId)
        }

        if (transactionId != null) {
            return ParsedResponse(PaymentSubmissionResult.Transaction(transactionId), transactionId)
        }
        throw PaymentError.Payment("Invalid payment result")
    }

    private fun isSuccessCode(code: Int?, hasChallenge: Boolean): Boolean =
        code == 200 || code == 201 || (code == null && hasChallenge)

    private fun isCheckoutThreeDS(transaction: ConfirmTransaction?): Boolean =
        transaction?.status.equals("pending-redirect", ignoreCase = true) &&
            transaction?.responseStatus.equals("3d-pending", ignoreCase = true)
}
