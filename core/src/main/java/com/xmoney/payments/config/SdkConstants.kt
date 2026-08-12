package com.xmoney.payments.config

object SdkConstants {
    const val SECURE_BASE_URL_STAGE = "https://secure-stage.xmoney.com"
    const val SECURE_BASE_URL_PROD = "https://secure.xmoney.com"
    const val API_NEXT_BASE_URL_STAGE = "https://api-stage-next.xmoney.com"
    const val API_NEXT_BASE_URL_PROD = "https://api-next.xmoney.com"

    const val CONFIRM_PAYMENT_PATH = "payment-request"

    const val SESSION_TOKEN_PATH = "api/v1/inline-checkout/session-token"
    const val CONFIG_PATH = "api/v1/inline-checkout/config"
    const val CARDS_PATH = "api/v1/inline-checkout/cards"
    const val TRANSACTIONS_PATH = "api/v1/inline-checkout/transactions"
    const val ACCOUNT_VALIDATION_PATH = "api/v1/account-validation"

    fun digitalWalletParamsPath(walletType: String) = "api/v1/digital-wallet/$walletType/params"

    const val GOOGLE_PAY_VALIDATE_PATH = "api/v1/digital-wallet/googlePay/validate-merchant"
}

class PaymentEnvironment private constructor(val isLive: Boolean) {
    val secureBaseURL: String
        get() = if (isLive) SdkConstants.SECURE_BASE_URL_PROD else SdkConstants.SECURE_BASE_URL_STAGE

    val apiNextBaseURL: String
        get() = if (isLive) SdkConstants.API_NEXT_BASE_URL_PROD else SdkConstants.API_NEXT_BASE_URL_STAGE

    val googlePayEnvironment: String
        get() = if (isLive) "PRODUCTION" else "TEST"

    companion object {
        fun from(publicKey: String): PaymentEnvironment? {
            val key = publicKey.lowercase()
            return when {
                key.contains("live") -> PaymentEnvironment(true)
                key.contains("test") -> PaymentEnvironment(false)
                else -> null
            }
        }
    }
}
