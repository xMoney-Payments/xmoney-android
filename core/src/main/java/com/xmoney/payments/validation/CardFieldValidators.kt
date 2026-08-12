package com.xmoney.payments.validation

import com.xmoney.payments.config.Strings

import java.util.Calendar

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
object CardFieldValidators {
    private val validator = CardNumberValidator()

    enum class FieldError(val messageKey: String) {
        CARD_NUMBER_REQUIRED("errors.cardNumberRequired"),
        CARD_NUMBER_UNSUPPORTED("errors.cardNumberUnsupported"),
        CARD_NUMBER_TOO_SHORT("errors.cardNumberTooShort"),
        CARD_NUMBER_WRONG_LENGTH("errors.cardNumberWrongLength"),
        CARD_NUMBER_INVALID("errors.cardNumberInvalid"),
        EXP_DATE_REQUIRED("errors.expDateRequired"),
        EXP_DATE_INVALID_FORMAT("errors.expDateInvalidFormat"),
        EXP_DATE_INVALID_MONTH("errors.expDateInvalidMonth"),
        CARD_EXPIRED("errors.cardExpired"),
        CVV_REQUIRED("errors.cvvRequired"),
        CVV_INVALID("errors.cvvInvalid"),
        CARD_HOLDER_NAME_REQUIRED("errors.cardHolderNameRequired"),
        CARD_HOLDER_NAME_NO_DIGITS("errors.cardHolderNameNoDigits"),
        CARD_HOLDER_NAME_INVALID_CHARS("errors.cardHolderNameInvalidChars"),
    }

    fun FieldError.localizedMessage(locale: String): String =
        Strings.text(messageKey, locale)

    fun detectBrand(rawNumber: String): String? = validator.detect(normalizeDigits(rawNumber))

    fun validateCardNumber(rawNumber: String): FieldError? {
        val number = normalizeDigits(rawNumber)
        if (number.isEmpty()) return FieldError.CARD_NUMBER_REQUIRED
        val brand = validator.detect(number)
        if (brand == null && number.length < 13) return FieldError.CARD_NUMBER_TOO_SHORT
        if (brand == null) return FieldError.CARD_NUMBER_UNSUPPORTED
        if (number.length < validator.minLength(number)) return FieldError.CARD_NUMBER_TOO_SHORT
        if (!validator.validateLength(number, brand)) return FieldError.CARD_NUMBER_WRONG_LENGTH
        if (!validator.checkLuhn(number)) return FieldError.CARD_NUMBER_INVALID
        return null
    }

    fun validateExpiry(month: String, year: String): FieldError? {
        if (month.isEmpty() || year.isEmpty()) return FieldError.EXP_DATE_REQUIRED
        val mm = month.toIntOrNull() ?: return FieldError.EXP_DATE_INVALID_FORMAT
        val yy = year.toIntOrNull() ?: return FieldError.EXP_DATE_INVALID_FORMAT
        if (mm < 1 || mm > 12) return FieldError.EXP_DATE_INVALID_MONTH
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR) % 100
        val currentMonth = now.get(Calendar.MONTH) + 1
        if (yy < currentYear || (yy == currentYear && mm < currentMonth)) return FieldError.CARD_EXPIRED
        return null
    }

    fun validateCVV(cvv: String): FieldError? {
        if (cvv.isEmpty()) return FieldError.CVV_REQUIRED
        val digits = normalizeDigits(cvv)
        if (digits.length < 3 || digits.length > 4) return FieldError.CVV_INVALID
        return null
    }

    fun validateHolderName(name: String?): FieldError? {
        val trimmed = (name ?: "").trim()
        if (trimmed.isEmpty()) return FieldError.CARD_HOLDER_NAME_REQUIRED
        if (trimmed.any { it.isDigit() }) return FieldError.CARD_HOLDER_NAME_NO_DIGITS
        val allowed = Regex("^[\\p{L} .'-]+$")
        if (!allowed.matches(trimmed)) return FieldError.CARD_HOLDER_NAME_INVALID_CHARS
        return if (trimmed.length <= 250) null else FieldError.CARD_HOLDER_NAME_INVALID_CHARS
    }

    fun normalizeDigits(value: String): String = value.filter { it.isDigit() }

    data class FormattedCard(val formatted: String, val raw: String, val brand: String?)

    fun formatCardNumber(raw: String): FormattedCard {
        val digits = normalizeDigits(raw)
        val brand = validator.detect(digits)
        val maxLen = validator.maxLength(digits)
        val capped = digits.take(maxLen)
        val grouped = capped.chunked(4).joinToString(" ")
        return FormattedCard(grouped, capped, brand)
    }

    fun normalizeExpiryDigits(raw: String): String {
        var digits = normalizeDigits(raw).take(4)
        if (digits.length >= 2 && (digits.take(2).toIntOrNull() ?: 0) > 12) {
            digits = "12" + digits.drop(2)
        }
        return digits
    }

    fun formatExpiry(raw: String): String {
        val digits = normalizeExpiryDigits(raw)
        if (digits.isEmpty()) return ""
        if (digits.length <= 2) return digits
        return "${digits.take(2)} / ${digits.drop(2)}"
    }

    fun parseExpiry(text: String): Pair<String, String> {
        val digits = normalizeDigits(text)
        if (digits.length < 2) return Pair(digits, "")
        return Pair(digits.take(2), digits.drop(2).take(2))
    }
}
