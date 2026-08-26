package com.xmoney.payments.model

import com.xmoney.payments.network.HttpClient

import org.json.JSONObject
import java.net.URI
import java.util.Base64

object OrderPayloadDecoder {
    fun decode(orderPayload: String): OrderInput? {
        val map = decodeMap(orderPayload) ?: return null
        return OrderInput.fromApiMap(map)
    }

    fun info(orderPayload: String): OrderPayloadInfo =
        decode(orderPayload)?.toInfo() ?: OrderPayloadInfo(
            cardTransactionMode = null,
            isVerifyCard = false,
            amount = null,
            currency = null,
            externalOrderId = null,
            isRecurring = false,
        )

    fun backUrlHost(orderPayload: String): String? = backUrl(orderPayload)?.host

    fun backUrl(orderPayload: String): URI? {
        val input = decode(orderPayload) ?: return null
        val backUrl = input.backUrl?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { URI(backUrl) }.getOrNull()
    }

    /**
     * Scheme + host + path-prefix match. Query/fragment are ignored.
     */
    fun matchesReturnURL(returnUrl: String, backUrl: URI): Boolean {
        val parsed = runCatching { URI(returnUrl) }.getOrNull() ?: return false
        return matchesReturnURL(parsed, backUrl)
    }

    fun matchesReturnURL(returnUrl: URI, backUrl: URI): Boolean {
        if ((returnUrl.scheme ?: "").lowercase() != (backUrl.scheme ?: "").lowercase()) return false
        if ((returnUrl.host ?: "").lowercase() != (backUrl.host ?: "").lowercase()) return false
        val returnPath = normalizedPath(returnUrl.path)
        val backPath = normalizedPath(backUrl.path)
        if (returnPath == backPath) return true
        if (backPath == "/") return false
        return returnPath.startsWith("$backPath/")
    }

    private fun normalizedPath(path: String?): String {
        if (path.isNullOrEmpty()) return "/"
        return if (path.length > 1 && path.endsWith("/")) path.dropLast(1) else path
    }

    private fun decodeMap(orderPayload: String): Map<String, Any?>? {
        return runCatching {
            val bytes = Base64.getDecoder().decode(padded(orderPayload))
            HttpClient.jsonToMap(JSONObject(String(bytes, Charsets.UTF_8)))
        }.getOrNull()
    }

    private fun padded(value: String): String {
        val remainder = value.length % 4
        if (remainder == 0) return value
        return value + "=".repeat(4 - remainder)
    }
}
