package com.xmoney.payments.model

import com.xmoney.payments.network.HttpClient

import org.json.JSONObject
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
