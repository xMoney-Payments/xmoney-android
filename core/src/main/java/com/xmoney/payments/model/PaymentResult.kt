package com.xmoney.payments.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class PaymentSubmissionResult {
    data class Needs3DS(
        val url: String,
        val formMethod: String = "GET",
        val params: Map<String, String> = emptyMap(),
    ) : PaymentSubmissionResult()
    data class Redirect(val url: String) : PaymentSubmissionResult()
    data class Transaction(val id: String) : PaymentSubmissionResult()
}

@Parcelize
data class TransactionCustomer(
    val id: String?,
    val siteId: String?,
    val identifier: String?,
    val firstName: String?,
    val lastName: String?,
    val country: String?,
    val state: String?,
    val city: String?,
    val zipCode: String?,
    val address: String?,
    val phone: String?,
    val email: String?,
    val isWhitelisted: Boolean = false,
    val isWhitelistedUntil: String? = null,
    val creationDate: String? = null,
    val creationTimestamp: Long? = null,
) : Parcelable {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): TransactionCustomer =
            TransactionCustomer(
                id = stringOrNumber(map["id"]),
                siteId = stringOrNumber(map["siteId"]),
                identifier = nonBlank(map["identifier"] as? String),
                firstName = nonBlank(map["firstName"] as? String),
                lastName = nonBlank(map["lastName"] as? String),
                country = nonBlank(map["country"] as? String),
                state = nonBlank(map["state"] as? String),
                city = nonBlank(map["city"] as? String),
                zipCode = nonBlank(map["zipCode"] as? String),
                address = nonBlank(map["address"] as? String),
                phone = nonBlank(map["phone"] as? String),
                email = nonBlank(map["email"] as? String),
                isWhitelisted = parseBoolean(map["isWhitelisted"]),
                isWhitelistedUntil = map["isWhitelistedUntil"] as? String,
                creationDate = map["creationDate"] as? String,
                creationTimestamp = (map["creationTimestamp"] as? Number)?.toLong(),
            )

        private fun stringOrNumber(value: Any?): String? = when (value) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is Number -> value.toString()
            else -> null
        }

        private fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

        private fun parseBoolean(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }
}

@Parcelize
data class Transaction(
    val id: String?,
    val status: String?,
    val amount: String? = null,
    val currencyKey: String? = null,
    val amountInEuro: String? = null,
    val externalOrderId: String? = null,
    val description: String? = null,
    val customerData: TransactionCustomer? = null,
) : Parcelable {
    val isComplete: Boolean
        get() = status?.contains("complete") == true

    val isSuccessfulComplete: Boolean
        get() = isComplete && status?.lowercase()?.contains("fail") != true

    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): Transaction {
            @Suppress("UNCHECKED_CAST")
            val customerMap = map["customerData"] as? Map<String, Any?>
            return Transaction(
                id = stringOrNumber(map["transactionId"]) ?: stringOrNumber(map["id"]),
                status = map["transactionStatus"] as? String
                    ?: map["status"] as? String,
                amount = map["amount"] as? String ?: (map["amount"] as? Number)?.toString(),
                currencyKey = map["currencyKey"] as? String,
                amountInEuro = map["amountInEuro"] as? String
                    ?: (map["amountInEuro"] as? Number)?.toString(),
                externalOrderId = map["externalOrderId"] as? String,
                description = map["description"] as? String,
                customerData = customerMap?.let { TransactionCustomer.fromApiMap(it) },
            )
        }

        private fun stringOrNumber(value: Any?): String? = when (value) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is Number -> value.toString()
            else -> null
        }
    }
}

/** Merchant-facing terminal outcome for Sheet, Element, and Google Pay. */
sealed class PaymentResult {
    data class Complete(val transaction: Transaction) : PaymentResult()
    data class Failed(val error: PaymentError) : PaymentResult()
    data object Canceled : PaymentResult()
}
