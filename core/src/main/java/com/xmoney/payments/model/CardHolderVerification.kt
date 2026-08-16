package com.xmoney.payments.model

enum class CardHolderMatchStatus(val raw: String) {
    MATCHED("Matched"),
    NOT_MATCHED("NotMatched"),
    NOT_VERIFIED("NotVerified"),
    PARTIAL_MATCHED("PartialMatched"),
    NOT_SUPPORTED("NotSupported"),
    ;

    companion object {
        fun from(raw: String?): CardHolderMatchStatus =
            entries.firstOrNull { it.raw == raw } ?: NOT_VERIFIED
    }
}

data class CardHolderVerificationResult(
    val status: CardHolderMatchStatus,
    val firstNameStatus: CardHolderMatchStatus? = null,
    val middleNameStatus: CardHolderMatchStatus? = null,
    val lastNameStatus: CardHolderMatchStatus? = null,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>?): CardHolderVerificationResult {
            if (map == null) {
                return CardHolderVerificationResult(status = CardHolderMatchStatus.NOT_VERIFIED)
            }
            fun status(key: String): CardHolderMatchStatus? =
                (map[key] as? String)?.let { CardHolderMatchStatus.from(it) }
            return CardHolderVerificationResult(
                status = CardHolderMatchStatus.from(map["status"] as? String),
                firstNameStatus = status("firstNameStatus"),
                middleNameStatus = status("middleNameStatus"),
                lastNameStatus = status("lastNameStatus"),
            )
        }
    }
}

data class AccountValidationResponse(
    val networkResponseCode: String?,
    val networkResponseCodeDescription: String?,
    val nameValidationResults: CardHolderVerificationResult,
) {
    companion object {
        internal fun fromApiMap(map: Map<String, Any?>): AccountValidationResponse {
            @Suppress("UNCHECKED_CAST")
            val results = map["nameValidationResults"] as? Map<String, Any?>
            return AccountValidationResponse(
                networkResponseCode = map["networkResponseCode"] as? String
                    ?: (map["networkResponseCode"] as? Number)?.toString(),
                networkResponseCodeDescription = map["networkResponseCodeDescription"] as? String,
                nameValidationResults = CardHolderVerificationResult.fromApiMap(results),
            )
        }
    }
}
