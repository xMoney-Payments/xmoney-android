package com.xmoney.payments.model

data class OrderInputCustomer(
    val identifier: String?,
    val firstName: String? = null,
    val lastName: String? = null,
    val country: String? = null,
    val city: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val tags: List<String> = emptyList(),
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): OrderInputCustomer =
            OrderInputCustomer(
                identifier = nonBlank(map["identifier"] as? String),
                firstName = nonBlank(map["firstName"] as? String),
                lastName = nonBlank(map["lastName"] as? String),
                country = nonBlank(map["country"] as? String),
                city = nonBlank(map["city"] as? String),
                phone = nonBlank(map["phone"] as? String),
                email = nonBlank(map["email"] as? String),
                tags = (map["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            )

        private fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    }
}

data class OrderInputOrder(
    val orderId: String?,
    val type: String?,
    val amount: Double?,
    val currency: String?,
    val description: String? = null,
    val intervalType: String? = null,
    val intervalValue: String? = null,
    val retryPayment: String? = null,
    val trialAmount: Double? = null,
    val firstBillDate: String? = null,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): OrderInputOrder =
            OrderInputOrder(
                orderId = map["orderId"] as? String,
                type = map["type"] as? String,
                amount = (map["amount"] as? Number)?.toDouble(),
                currency = map["currency"] as? String,
                description = map["description"] as? String,
                intervalType = map["intervalType"] as? String,
                intervalValue = map["intervalValue"] as? String
                    ?: (map["intervalValue"] as? Number)?.toString(),
                retryPayment = map["retryPayment"] as? String,
                trialAmount = (map["trialAmount"] as? Number)?.toDouble(),
                firstBillDate = map["firstBillDate"] as? String,
            )
    }
}

data class OrderInput(
    val publicKey: String? = null,
    val cardTransactionMode: String? = null,
    val invoiceEmail: String? = null,
    val saveCard: Boolean = false,
    val cardId: String? = null,
    val backUrl: String? = null,
    val customData: String? = null,
    val customer: OrderInputCustomer? = null,
    val order: OrderInputOrder? = null,
) {
    fun toInfo(): OrderPayloadInfo {
        val mode = cardTransactionMode
        return OrderPayloadInfo(
            cardTransactionMode = mode,
            isVerifyCard = mode == "verifyCard",
            amount = order?.amount,
            currency = order?.currency,
            externalOrderId = order?.orderId,
            isRecurring = order?.type == "recurring",
        )
    }

    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): OrderInput {
            @Suppress("UNCHECKED_CAST")
            val customerMap = map["customer"] as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val orderMap = map["order"] as? Map<String, Any?>
            return OrderInput(
                publicKey = map["publicKey"] as? String,
                cardTransactionMode = map["cardTransactionMode"] as? String,
                invoiceEmail = map["invoiceEmail"] as? String,
                saveCard = parseBoolean(map["saveCard"]),
                cardId = stringOrNumber(map["cardId"]),
                backUrl = map["backUrl"] as? String,
                customData = map["customData"] as? String,
                customer = customerMap?.let { OrderInputCustomer.fromApiMap(it) },
                order = orderMap?.let { OrderInputOrder.fromApiMap(it) },
            )
        }

        private fun stringOrNumber(value: Any?): String? = when (value) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is Number -> value.toString()
            else -> null
        }

        private fun parseBoolean(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }
}

data class OrderPayloadInfo(
    val cardTransactionMode: String?,
    val isVerifyCard: Boolean,
    val amount: Double?,
    val currency: String?,
    val externalOrderId: String?,
    val isRecurring: Boolean,
)

data class SavedCard(
    val id: String,
    val cardNumber: String?,
    val cardType: String?,
    val cardExpiryDate: String?,
    val isDefault: Boolean = false,
    val bankName: String? = null,
    val cardBrand: String? = null,
    val nameOnCard: String? = null,
    val customerId: String? = null,
    val cardHolderCountry: String? = null,
) {
    companion object {
        internal fun fromApiMap(item: Map<String, Any?>): SavedCard? {
            val id = stringOrNumber(item["id"]) ?: return null
            val binInfo = item["binInfo"] as? Map<String, Any?>
            val cardType = nonBlank(item["cardType"] as? String)
                ?: nonBlank(item["type"] as? String)

            val bankName = nonBlank(item["bankName"] as? String)

            val cardBrand = nonBlank(item["cardBrand"] as? String)
                ?: nonBlank(binInfo?.get("brand") as? String)
                ?: cardType?.takeIf { isKnownCardBrand(it) }

            return SavedCard(
                id = id,
                cardNumber = item["cardNumber"] as? String,
                cardType = cardType,
                cardExpiryDate = parseExpiryDate(item),
                isDefault = parseBoolean(item["isDefault"]) || parseBoolean(item["default"]),
                bankName = bankName,
                cardBrand = cardBrand,
                nameOnCard = nonBlank(item["nameOnCard"] as? String),
                customerId = stringOrNumber(item["customerId"]),
                cardHolderCountry = nonBlank(item["cardHolderCountry"] as? String),
            )
        }

        private fun parseExpiryDate(item: Map<String, Any?>): String? {
            nonBlank(item["cardExpiryDate"] as? String)?.let { return it }
            val month = nonBlank(item["expiryMonth"] as? String)
                ?: (item["expiryMonth"] as? Number)?.toString()
            val year = nonBlank(item["expiryYear"] as? String)
                ?: (item["expiryYear"] as? Number)?.toString()
            if (month.isNullOrEmpty() || year.isNullOrEmpty()) return null
            val shortYear = if (year.length == 4) year.takeLast(2) else year
            return "$month/$shortYear"
        }

        private fun isKnownCardBrand(value: String): Boolean {
            val normalized = value.trim().lowercase()
            return normalized in setOf(
                "visa",
                "mastercard",
                "maestro",
                "amex",
                "american express",
                "discover",
                "diners",
                "jcb",
            )
        }

        private fun parseBoolean(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }

        private fun stringOrNumber(value: Any?): String? = when (value) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is Number -> value.toString()
            else -> null
        }

        private fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    }
}

data class SavedCardsResponse(
    val data: List<SavedCard>,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): SavedCardsResponse {
            val raw = map["data"] as? List<*> ?: emptyList<Any?>()
            val cards = raw.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                val entry = item as? Map<String, Any?> ?: return@mapNotNull null
                SavedCard.fromApiMap(entry)
            }
            return SavedCardsResponse(data = cards)
        }
    }
}

data class CardInput(
    val number: String = "",
    val expiryMonth: String = "",
    val expiryYear: String = "",
    val cvv: String = "",
    val holderName: String? = null,
    val saveCard: Boolean = false,
) {
    override fun toString(): String =
        "CardInput(number=****, expiryMonth=$expiryMonth, expiryYear=$expiryYear, " +
            "cvv=***, holderName=${holderName?.let { "***" }}, saveCard=$saveCard)"
}

data class WalletParams(
    val gateway: String?,
    val gatewayMerchantId: String?,
    val merchantId: String?,
    val merchantName: String?,
    val merchantCountry: String?,
    val supportedNetworks: List<String>,
    val merchantOrigin: String? = null,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): WalletParams {
            @Suppress("UNCHECKED_CAST")
            val data = (map["data"] as? Map<String, Any?>) ?: map
            return WalletParams(
                gateway = data["gateway"] as? String,
                gatewayMerchantId = data["gatewayMerchantId"] as? String,
                merchantId = data["merchantId"] as? String,
                merchantName = data["merchantName"] as? String,
                merchantCountry = data["merchantCountry"] as? String,
                merchantOrigin = (data["merchantOrigin"] as? String)?.takeIf { it.isNotBlank() },
                supportedNetworks = stringList(data["allowedCardNetworks"])
                    .ifEmpty { stringList(data["supportedNetworks"]) },
            )
        }

        private fun stringList(value: Any?): List<String> =
            (value as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    }
}
