package com.xmoney.payments.customerdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerDetailsVisibilityTest {
    @Test
    fun emptyListIsInactive() {
        val visibility = CustomerDetailsVisibility.resolve(emptyList())
        assertFalse(visibility.isActive)
        assertFalse(visibility.showFullName)
        assertFalse(visibility.showEmail)
        assertFalse(visibility.showPhone)
        assertFalse(visibility.showAddressSection)
    }

    @Test
    fun firstOrLastNameShowsFullName() {
        assertTrue(CustomerDetailsVisibility.resolve(listOf("firstName")).showFullName)
        assertTrue(CustomerDetailsVisibility.resolve(listOf("lastName")).showFullName)
        assertFalse(CustomerDetailsVisibility.resolve(listOf("email")).showFullName)
    }

    @Test
    fun anyAddressFieldShowsTheWholeSection() {
        for (field in listOf("country", "state", "city", "zipCode", "address")) {
            val visibility = CustomerDetailsVisibility.resolve(listOf(field))
            assertTrue(field, visibility.showAddressSection)
            assertTrue(visibility.isActive)
        }
    }

    @Test
    fun emailAndPhoneAreIndependent() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email", "phone"))
        assertTrue(visibility.showEmail)
        assertTrue(visibility.showPhone)
        assertFalse(visibility.showAddressSection)
        assertEquals(
            listOf(PersonalField.PHONE_COUNTRY, PersonalField.PHONE, PersonalField.EMAIL),
            CustomerDetailsValidator.activeFields(visibility, countryCode = null),
        )
    }
}
