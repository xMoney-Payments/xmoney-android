package com.xmoney.payments.model

sealed class PaymentError(
    val code: String,
    override val message: String,
) : Exception(message) {
    class Network(message: String = GENERIC_NETWORK) : PaymentError("NETWORK_ERROR", message)
    class Session(message: String = "Missing session token") : PaymentError("SESSION_ERROR", message)
    class Payment(message: String) : PaymentError("PAYMENT_ERROR", message)
    class ThreeDS(message: String = "Missing 3DS URL") : PaymentError("THREE_DS_ERROR", message)
    class PollTimeout(message: String = "Polling timed out") : PaymentError("POLL_TIMEOUT", message)
    class InvalidKey(message: String = "Invalid public key") : PaymentError("INVALID_PUBLIC_KEY", message)
    class GooglePay(message: String) : PaymentError("GOOGLE_PAY", message)
    class Load(message: String) : PaymentError("LOAD_ERROR", message)
    class Canceled : PaymentError("CANCELED", "Payment canceled")
    class CardHolderVerification(
        message: String = NAME_CHECK_NOT_ENABLED,
    ) : PaymentError("CARD_HOLDER_VERIFICATION", message)
    class Unknown(code: String, message: String) : PaymentError(code, message)

    fun merchantMessage(): String = when (this) {
        is Network -> GENERIC_NETWORK
        is Unknown -> GENERIC_REQUEST
        is Payment -> if (isSdkAuthored(message)) message else GENERIC_PAYMENT
        is Load -> if (isSdkAuthored(message)) message else GENERIC_LOAD
        is GooglePay -> if (isSdkAuthored(message)) message else GENERIC_GOOGLE_PAY
        else -> message
    }

    companion object {
        const val NAME_CHECK_NOT_ENABLED =
            "Card holder name verification is not enabled for this site. Remove card holder verification or contact support."
        const val VERIFICATION_REJECTED =
            "Card owner verification rejected by cardOwnerVerificationCallback"

        const val GENERIC_NETWORK = "Network request failed"
        const val GENERIC_REQUEST = "Request failed"
        const val GENERIC_PAYMENT = "Payment failed"
        const val GENERIC_LOAD = "Failed to load"
        const val GENERIC_GOOGLE_PAY = "Google Pay failed"

        private val sdkAuthoredMessages = setOf(
            NAME_CHECK_NOT_ENABLED,
            VERIFICATION_REJECTED,
            "Missing session token",
            "Missing 3DS URL",
            "Polling timed out",
            "Invalid public key",
            "Payment canceled",
            "Missing currency for card holder verification",
            "Missing transaction id",
            "Google Pay is not available",
            "Google Pay resolution launcher not registered",
            GENERIC_NETWORK,
            GENERIC_REQUEST,
            GENERIC_PAYMENT,
            GENERIC_LOAD,
            GENERIC_GOOGLE_PAY,
        )

        private fun isSdkAuthored(message: String): Boolean =
            message in sdkAuthoredMessages || message.startsWith("Transaction ")

        fun from(code: String, message: String): PaymentError = when (code) {
            "NETWORK_ERROR" -> Network(message)
            "SESSION_ERROR" -> Session(message)
            "PAYMENT_ERROR" -> Payment(message)
            "THREE_DS_ERROR" -> ThreeDS(message)
            "POLL_TIMEOUT" -> PollTimeout(message)
            "INVALID_PUBLIC_KEY" -> InvalidKey(message)
            "GOOGLE_PAY" -> GooglePay(message)
            "LOAD_ERROR" -> Load(message)
            "CARD_HOLDER_VERIFICATION" -> CardHolderVerification(message)
            "CANCELED" -> Canceled()
            else -> Unknown(code, message)
        }
    }
}
