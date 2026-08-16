package com.xmoney.paymentelement.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

class CardInputTransformationsTest {
    @Test
    fun cardNumber_offsetMapping_roundTripsAtBoundaries() {
        val digits = "1234567890123456"
        val mapping = offsetMapping(CardNumberVisualTransformation, digits)
        val formatted = digits.chunked(4).joinToString(" ")

        assertEquals("1234 5678 9012 3456", formatted)
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(6, mapping.originalToTransformed(5))
        assertEquals(formatted.length, mapping.originalToTransformed(digits.length))

        assertEquals(0, mapping.transformedToOriginal(0))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(5, mapping.transformedToOriginal(6))
        assertEquals(digits.length, mapping.transformedToOriginal(formatted.length))
    }

    @Test
    fun expiry_offsetMapping_handlesSeparator() {
        val digits = "1226"
        val mapping = offsetMapping(ExpiryVisualTransformation, digits)

        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(6, mapping.originalToTransformed(3))
        assertEquals(7, mapping.originalToTransformed(4))

        assertEquals(2, mapping.transformedToOriginal(3))
        assertEquals(2, mapping.transformedToOriginal(4))
        assertEquals(2, mapping.transformedToOriginal(5))
        assertEquals(3, mapping.transformedToOriginal(6))
        assertEquals(4, mapping.transformedToOriginal(7))
    }

    @Test
    fun expiry_offsetMapping_beforeSeparatorInserted() {
        val digits = "12"
        val mapping = offsetMapping(ExpiryVisualTransformation, digits)

        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(2, mapping.transformedToOriginal(2))
    }

    @Test
    fun expiry_display_formatsMonthAndYear() {
        val transformed = ExpiryVisualTransformation.filter(AnnotatedString("1226"))
        assertEquals("12 / 26", transformed.text.text)
    }

    private fun offsetMapping(transformation: VisualTransformation, text: String): OffsetMapping {
        val result: TransformedText = transformation.filter(AnnotatedString(text))
        return result.offsetMapping
    }
}
