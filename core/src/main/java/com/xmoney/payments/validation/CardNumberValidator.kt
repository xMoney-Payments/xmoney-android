package com.xmoney.payments.validation

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
class CardNumberValidator {
    private data class BinRange(val low: Int, val high: Int, val brand: String)

    private val reverseBinMap = listOf(
        BinRange(622126, 622925, "unionpay"),
        BinRange(624000, 626999, "unionpay"),
        BinRange(628200, 628899, "unionpay"),
        BinRange(2200, 2204, "mir"),
        BinRange(2221, 2720, "mastercard"),
        BinRange(3095, 3095, "diners"),
        BinRange(3528, 3589, "jcb"),
        BinRange(5019, 5019, "dankort"),
        BinRange(6011, 6011, "discover"),
        BinRange(300, 305, "diners"),
        BinRange(644, 649, "discover"),
        BinRange(34, 34, "amex"),
        BinRange(36, 36, "diners"),
        BinRange(37, 37, "amex"),
        BinRange(38, 39, "diners"),
        BinRange(50, 50, "maestro"),
        BinRange(51, 55, "mastercard"),
        BinRange(56, 58, "maestro"),
        BinRange(65, 65, "discover"),
        BinRange(4, 4, "visa"),
        BinRange(6, 6, "maestro"),
    )

    private val cardLength = mapOf(
        "amex" to listOf(15),
        "diners" to listOf(14),
        "discover" to listOf(16, 19),
        "jcb" to listOf(15, 16),
        "maestro" to listOf(12, 13, 14, 15, 16, 17, 18, 19),
        "mastercard" to listOf(16),
        "unionpay" to listOf(16, 17, 18, 19),
        "visa" to listOf(13, 16, 19),
        "dankort" to listOf(16),
        "mir" to listOf(16),
    )

    fun detect(pan: String): String? {
        for (entry in reverseBinMap) {
            val length = entry.low.toString().length
            if (pan.length < length) continue
            val value = pan.substring(0, length).toIntOrNull() ?: continue
            if (value in entry.low..entry.high) return entry.brand
        }
        return null
    }

    fun validateLength(pan: String, brand: String? = null): Boolean {
        val resolved = brand ?: detect(pan) ?: return false
        val lengths = cardLength[resolved] ?: return false
        return lengths.contains(pan.length)
    }

    fun maxLength(pan: String): Int {
        val brand = detect(pan) ?: return 19
        return cardLength[brand]?.maxOrNull() ?: 19
    }

    fun minLength(pan: String): Int {
        val brand = detect(pan) ?: return 0
        return cardLength[brand]?.minOrNull() ?: 0
    }

    fun checkLuhn(pan: String): Boolean {
        var sum = 0
        var shouldDouble = false
        for (char in pan.reversed()) {
            var digit = Character.digit(char, 10)
            if (digit < 0) return false
            if (shouldDouble) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return sum % 10 == 0
    }
}
