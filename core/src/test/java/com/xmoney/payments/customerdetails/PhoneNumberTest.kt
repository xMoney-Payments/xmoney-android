package com.xmoney.payments.customerdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberTest {
    @Test
    fun acceptsSixToFifteenDigitsAfterFormatting() {
        assertTrue(PhoneNumber.isValid("0722 111 222"))
        assertTrue(PhoneNumber.isValid("(415) 555-0134"))
        assertTrue(PhoneNumber.isValid("123456"))
        assertTrue(PhoneNumber.isValid("123456789012345"))
        assertFalse(PhoneNumber.isValid("12345"))
        assertFalse(PhoneNumber.isValid("1234567890123456"))
        assertFalse(PhoneNumber.isValid("12345a"))
        assertFalse(PhoneNumber.isValid(""))
    }

    @Test
    fun payloadDropsOneLeadingZeroWhenLengthRemainsValid() {
        assertEquals("722111222", PhoneNumber.formatForPayload(PhoneNumber.normalizeDigits("0722 111 222")))
        assertEquals("01234", PhoneNumber.formatForPayload(PhoneNumber.normalizeDigits("01234")))
    }
}
