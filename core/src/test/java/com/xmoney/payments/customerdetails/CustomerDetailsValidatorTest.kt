package com.xmoney.payments.customerdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerDetailsValidatorTest {
    private val all = CustomerDetailsVisibility.resolve(
        listOf("firstName", "email", "phone", "country", "state", "city", "zipCode", "address"),
    )

    @Test
    fun requiresActiveFields() {
        val errors = CustomerDetailsValidator.errors(CustomerDetailsInput(), all)
        assertEquals("personalDetails.errors.fullNameRequired", errors[PersonalField.FULL_NAME])
        assertEquals("personalDetails.errors.emailRequired", errors[PersonalField.EMAIL])
        assertEquals("personalDetails.errors.phoneCountryRequired", errors[PersonalField.PHONE_COUNTRY])
        assertEquals("personalDetails.errors.phoneRequired", errors[PersonalField.PHONE])
        assertEquals("personalDetails.errors.countryRequired", errors[PersonalField.COUNTRY])
        assertEquals("personalDetails.errors.addressRequired", errors[PersonalField.ADDRESS])
        assertEquals("personalDetails.errors.cityRequired", errors[PersonalField.CITY])
    }

    @Test
    fun regionAndPostalFollowTheSelectedCountry() {
        val romania = CustomerDetailsInput(country = "RO", postalCode = "12")
        assertNull(CustomerDetailsValidator.validate(PersonalField.REGION, romania, all))
        assertEquals(
            "personalDetails.errors.postalCodeInvalid",
            CustomerDetailsValidator.validate(PersonalField.POSTAL, romania, all),
        )

        val us = CustomerDetailsInput(country = "US", region = "CA", postalCode = "94105")
        assertNull(CustomerDetailsValidator.validate(PersonalField.REGION, us, all))
        assertNull(CustomerDetailsValidator.validate(PersonalField.POSTAL, us, all))
        assertEquals(
            "personalDetails.errors.stateRequired",
            CustomerDetailsValidator.validate(PersonalField.REGION, us.copy(region = ""), all),
        )
    }

    @Test
    fun emiratesHidePostalAndRequireEmirate() {
        val input = CustomerDetailsInput(country = "AE")
        assertNull(CustomerDetailsValidator.validate(PersonalField.POSTAL, input, all))
        assertEquals(
            "personalDetails.errors.emirateRequired",
            CustomerDetailsValidator.validate(PersonalField.REGION, input, all),
        )
        assertTrue(PersonalField.POSTAL !in CustomerDetailsValidator.activeFields(all, "AE"))
        assertTrue(PersonalField.REGION in CustomerDetailsValidator.activeFields(all, "AE"))
    }

    @Test
    fun emailAndPhoneFormats() {
        assertEquals(
            "personalDetails.errors.emailInvalid",
            CustomerDetailsValidator.validate(PersonalField.EMAIL, CustomerDetailsInput(email = "not-an-email"), all),
        )
        assertNull(
            CustomerDetailsValidator.validate(PersonalField.EMAIL, CustomerDetailsInput(email = "a@b.co"), all),
        )
        assertEquals(
            "personalDetails.errors.phoneInvalid",
            CustomerDetailsValidator.validate(PersonalField.PHONE, CustomerDetailsInput(phoneNational = "12"), all),
        )
        assertNull(
            CustomerDetailsValidator.validate(
                PersonalField.PHONE,
                CustomerDetailsInput(phoneNational = "0722 111 222"),
                all,
            ),
        )
    }
}
