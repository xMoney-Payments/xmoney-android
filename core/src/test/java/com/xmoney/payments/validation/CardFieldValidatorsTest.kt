package com.xmoney.payments.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFieldValidatorsTest {
    @Test
    fun cardNumberRequired() {
        assertEquals(
            CardFieldValidators.FieldError.CARD_NUMBER_REQUIRED,
            CardFieldValidators.validateCardNumber(""),
        )
    }

    @Test
    fun cardNumberValid() {
        assertNull(CardFieldValidators.validateCardNumber("4111111111111111"))
    }

    @Test
    fun cardNumberTooShort() {
        assertEquals(
            CardFieldValidators.FieldError.CARD_NUMBER_TOO_SHORT,
            CardFieldValidators.validateCardNumber("123"),
        )
    }

    @Test
    fun cvvRequired() {
        assertEquals(CardFieldValidators.FieldError.CVV_REQUIRED, CardFieldValidators.validateCVV(""))
    }

    @Test
    fun cvvInvalid() {
        assertEquals(CardFieldValidators.FieldError.CVV_INVALID, CardFieldValidators.validateCVV("12"))
    }

    @Test
    fun holderNameRequired() {
        assertEquals(
            CardFieldValidators.FieldError.CARD_HOLDER_NAME_REQUIRED,
            CardFieldValidators.validateHolderName(""),
        )
    }

    @Test
    fun holderNameNoDigits() {
        assertEquals(
            CardFieldValidators.FieldError.CARD_HOLDER_NAME_NO_DIGITS,
            CardFieldValidators.validateHolderName("John123"),
        )
    }

    @Test
    fun normalizeExpiryDigits_clampsMonthAboveTwelve() {
        assertEquals("1299", CardFieldValidators.normalizeExpiryDigits("1399"))
        assertEquals("1226", CardFieldValidators.normalizeExpiryDigits("1226"))
        assertEquals("12", CardFieldValidators.normalizeExpiryDigits("12"))
    }

    @Test
    fun formatExpiry_matchesVisualDisplay() {
        assertEquals("12 / 26", CardFieldValidators.formatExpiry("1226"))
    }
}
