package com.xmoney.payments.model

data class SessionTokenResponse(
    val token: String?,
) {
    companion object {
        fun fromApiMap(map: Map<String, Any?>): SessionTokenResponse =
            SessionTokenResponse(token = map["token"] as? String)
    }
}

data class SiteConfig(
    val whitelabelPaymentForm: Boolean = false,
    val checkNameWithoutSaveCard: Boolean = false,
    val nameCheckValidationEnabled: Boolean = false,
) {
    companion object {
        fun fromApiMap(map: Map<String, Any?>): SiteConfig =
            SiteConfig(
                whitelabelPaymentForm = map["whitelabelPaymentForm"] == true,
                checkNameWithoutSaveCard = map["checkNameWithoutSaveCard"] == true,
                nameCheckValidationEnabled = map["nameCheckValidationEnabled"] == true,
            )
    }
}

data class ConfirmTransaction(
    val transactionId: String?,
    val status: String?,
    val responseStatus: String?,
    val redirectUrl: String?,
) {
    companion object {
        fun fromApiMap(map: Map<String, Any?>): ConfirmTransaction {
            val transactionId = (map["transactionId"] as? String)
                ?: (map["transactionId"] as? Number)?.toString()
            return ConfirmTransaction(
                transactionId = transactionId,
                status = map["status"] as? String,
                responseStatus = map["responseStatus"] as? String,
                redirectUrl = map["redirectUrl"] as? String,
            )
        }
    }
}

data class ConfirmPaymentData(
    val transaction: ConfirmTransaction?,
    val threeDSFlowUrl: String?,
    val result: String?,
    val orderRequestBackUrl: String?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromApiMap(map: Map<String, Any?>): ConfirmPaymentData {
            val transactionMap = map["transaction"] as? Map<String, Any?>
            val orderRequest = map["orderRequest"] as? Map<String, Any?>
            val processing = orderRequest?.get("processing") as? Map<String, Any?>
            return ConfirmPaymentData(
                transaction = transactionMap?.let { ConfirmTransaction.fromApiMap(it) },
                threeDSFlowUrl = map["threeDSFlowUrl"] as? String,
                result = map["result"] as? String,
                orderRequestBackUrl = processing?.get("backUrl") as? String,
            )
        }
    }
}

data class ConfirmPaymentResponse(
    val code: Int?,
    val status: String?,
    val data: ConfirmPaymentData?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromApiMap(map: Map<String, Any?>): ConfirmPaymentResponse {
            val dataMap = map["data"] as? Map<String, Any?>
            return ConfirmPaymentResponse(
                code = (map["code"] as? Number)?.toInt(),
                status = map["status"] as? String,
                data = dataMap?.let { ConfirmPaymentData.fromApiMap(it) },
            )
        }
    }
}
