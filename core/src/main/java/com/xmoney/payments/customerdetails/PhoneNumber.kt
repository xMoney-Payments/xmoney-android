package com.xmoney.payments.customerdetails

/**
 * Validation accepts 6–15 digits after spaces, dashes, parentheses, and dots,
 * including a leading 0. The payload drops one leading 0 only when 6–15
 * digits remain. A leading `+` is not formatting, so a pasted international
 * number stays invalid. Matches `phone-validation.utils.js`.
 */
internal object PhoneNumber {
    private const val MIN_DIGITS = 6
    private const val MAX_DIGITS = 15
    private val formatting = Regex("[\\s\\-().]")

    fun normalizeDigits(nationalInput: String?): String =
        nationalInput.orEmpty().replace(formatting, "").replace(Regex("\\D"), "")

    fun formatForPayload(digits: String?): String {
        if (digits.isNullOrEmpty()) return ""
        if (digits.startsWith("0")) {
            val stripped = digits.drop(1)
            if (stripped.length in MIN_DIGITS..MAX_DIGITS) return stripped
        }
        return digits
    }

    fun isValid(nationalInput: String?): Boolean {
        val trimmed = nationalInput.orEmpty().trim()
        if (trimmed.isEmpty()) return false
        val withoutFormatting = trimmed.replace(formatting, "")
        if (!withoutFormatting.matches(Regex("^\\d+$"))) return false
        return withoutFormatting.length in MIN_DIGITS..MAX_DIGITS
    }
}
