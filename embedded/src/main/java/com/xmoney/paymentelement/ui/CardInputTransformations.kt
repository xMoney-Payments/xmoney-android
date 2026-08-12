package com.xmoney.paymentelement.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal object CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = digits.chunked(4).joinToString(" ")
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val clamped = offset.coerceIn(0, digits.length)
                    var transformed = 0
                    for (i in 0 until clamped) {
                        transformed++
                        if ((i + 1) % 4 == 0 && i + 1 < digits.length) {
                            transformed++
                        }
                    }
                    return transformed.coerceIn(0, formatted.length)
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val clamped = offset.coerceIn(0, formatted.length)
                    var original = 0
                    var transformed = 0
                    while (transformed < clamped) {
                        if (transformed < formatted.length && formatted[transformed] == ' ') {
                            transformed++
                            continue
                        }
                        transformed++
                        original++
                    }
                    return original.coerceIn(0, digits.length)
                }
            },
        )
    }
}

internal object ExpiryVisualTransformation : VisualTransformation {
    private const val SEPARATOR = " / "

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = formatExpiryDisplay(digits)
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val i = offset.coerceIn(0, digits.length)
                    if (digits.length <= 2) return i.coerceIn(0, formatted.length)
                    return if (i <= 2) {
                        i
                    } else {
                        (i + SEPARATOR.length).coerceIn(0, formatted.length)
                    }
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val i = offset.coerceIn(0, formatted.length)
                    if (digits.length <= 2) return i.coerceIn(0, digits.length)
                    return when {
                        i <= 2 -> i
                        i < 2 + SEPARATOR.length -> 2
                        else -> (i - SEPARATOR.length).coerceIn(0, digits.length)
                    }
                }
            },
        )
    }

    private fun formatExpiryDisplay(digits: String): String {
        if (digits.isEmpty()) return ""
        if (digits.length <= 2) return digits
        return "${digits.take(2)}$SEPARATOR${digits.drop(2)}"
    }
}
