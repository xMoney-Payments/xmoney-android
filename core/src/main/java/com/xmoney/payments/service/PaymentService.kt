package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.ConfirmPaymentResponse
import com.xmoney.payments.model.PaymentSubmissionResult
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.network.HttpClient
import com.xmoney.payments.util.DeviceMetadata
import com.xmoney.payments.validation.CardFieldValidators

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
        val url = "${env.secureBaseURL}/${SdkConstants.CONFIRM_PAYMENT_PATH}"
        return ConfirmPaymentResponse.fromApiMap(http.postMultipart(url, fields))
    }
    @androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

    data class ParsedResponse(val submission: PaymentSubmissionResult, val transactionId: String?)

    fun parse(result: ConfirmPaymentResponse): ParsedResponse {
        val data = result.data
        if (data == null || result.code != 200) {
            val message = result.status?.let { "Payment error: $it" }
                ?: "Invalid payment result"
            throw PaymentError.Payment(message)
        }

        val transaction = data.transaction
        val transactionId = transaction?.transactionId
        val status = transaction?.status
        val responseStatus = transaction?.responseStatus

        if (status == "pending-redirect" && responseStatus == "3d-pending") {
            val urlString = data.threeDSFlowUrl ?: transaction?.redirectUrl
            if (urlString != null) {
                return ParsedResponse(PaymentSubmissionResult.Needs3DS(urlString), transactionId)
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
}
