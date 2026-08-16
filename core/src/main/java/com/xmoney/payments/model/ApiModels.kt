package com.xmoney.payments.model

data class SessionTokenResponse(
    val token: String?,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): SessionTokenResponse =
            SessionTokenResponse(token = map["token"] as? String)
    }
}

data class SiteConfig(
    val whitelabelPaymentForm: Boolean = false,
    val checkNameWithoutSaveCard: Boolean = false,
    val nameCheckValidationEnabled: Boolean = false,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): SiteConfig =
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
        internal fun fromApiMap(map: Map<String, Any?>): ConfirmTransaction =
            ConfirmTransaction(
                transactionId = stringOrNumber(map["transactionId"]),
                status = map["status"] as? String,
                responseStatus = map["responseStatus"] as? String,
                redirectUrl = (map["redirectUrl"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ((map["redirect"] as? Map<*, *>)?.get("url") as? String)?.trim()?.takeIf { it.isNotEmpty() },
            )
    }
}

data class ConfirmPaymentData(
    val transaction: ConfirmTransaction?,
    val threeDSFlowUrl: String?,
    val result: String?,
    val orderRequestBackUrl: String?,
    val transactionId: String? = null,
    val is3d: Boolean = false,
    val isRedirect: Boolean = false,
    val redirectUrl: String? = null,
    val redirectFormMethod: String = "GET",
    val redirectParams: Map<String, String> = emptyMap(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun fromApiMap(map: Map<String, Any?>): ConfirmPaymentData {
            val transactionMap = map["transaction"] as? Map<String, Any?>
            val orderRequest = map["orderRequest"] as? Map<String, Any?>
            val processing = orderRequest?.get("processing") as? Map<String, Any?>
            val redirectMap = map["redirect"] as? Map<String, Any?>
            val paramsMap = redirectMap?.get("params") as? Map<String, Any?>
            val params = LinkedHashMap<String, String>()
            paramsMap?.forEach { (key, value) ->
                if (value != null) params[key] = value.toString()
            }
            val method = (redirectMap?.get("formMethod") as? String)?.trim()?.uppercase().orEmpty()
            val nestedRedirectUrl = (redirectMap?.get("url") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val stringRedirect = (map["redirect"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val topLevelRedirectUrl = (map["redirectUrl"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            return ConfirmPaymentData(
                transaction = transactionMap?.let { ConfirmTransaction.fromApiMap(it) },
                threeDSFlowUrl = (map["threeDSFlowUrl"] as? String)
                    ?: (transactionMap?.get("threeDSFlowUrl") as? String),
                result = map["result"] as? String,
                orderRequestBackUrl = processing?.get("backUrl") as? String,
                transactionId = stringOrNumber(map["transactionId"]),
                is3d = isTruthyFlag(map["is3d"]),
                isRedirect = isTruthyFlag(map["isRedirect"]),
                redirectUrl = nestedRedirectUrl ?: stringRedirect ?: topLevelRedirectUrl,
                redirectFormMethod = method.ifEmpty { "GET" },
                redirectParams = params,
            )
        }
    }
}

internal fun stringOrNumber(value: Any?): String? = when (value) {
    is String -> value.trim().takeIf { it.isNotEmpty() }
    is Number -> value.toString()
    else -> null
}

internal fun intOrString(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
}

internal fun isTruthyFlag(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.equals("true", ignoreCase = true) || value == "1"
    else -> false
}

data class ConfirmPaymentResponse(
    val code: Int?,
    val status: String?,
    val data: ConfirmPaymentData?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun fromApiMap(map: Map<String, Any?>): ConfirmPaymentResponse {
            val dataMap = map["data"] as? Map<String, Any?>
            return ConfirmPaymentResponse(
                code = intOrString(map["code"]),
                status = map["status"] as? String,
                data = dataMap?.let { ConfirmPaymentData.fromApiMap(it) },
            )
        }
    }
}
