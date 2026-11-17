package com.xmoney.payments.customerdetails

import com.xmoney.payments.config.CustomerDetailsDefaults
import com.xmoney.payments.config.CustomerDetailsFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerDetailsDefaultsTest {
    @Test
    fun fieldsOverrideReplacesTheServerListAndMissingKeysAreOff() {
        val contact = CustomerDetailsResolver.inlineCheckoutFields(
            CustomerDetailsFields(fullName = true, email = true),
        )
        val visibility = CustomerDetailsVisibility.resolve(contact)
        assertEquals(listOf("firstName", "lastName", "email"), contact)
        assertTrue(visibility.showFullName)
        assertTrue(visibility.showEmail)
        assertFalse(visibility.showPhone)
        assertFalse(visibility.showAddressSection)

        val address = CustomerDetailsVisibility.resolve(
            CustomerDetailsResolver.inlineCheckoutFields(
                CustomerDetailsFields(phone = true, address = true),
            ),
        )
        assertTrue(address.showPhone)
        assertTrue(address.showAddressSection)
        assertFalse(address.showFullName)
        assertFalse(address.showEmail)

        assertFalse(
            CustomerDetailsVisibility.resolve(
                CustomerDetailsResolver.inlineCheckoutFields(CustomerDetailsFields()),
            ).isActive,
        )
    }

    @Test
    fun parsePhoneSplitsE164AndPrefersSharedDialCodes() {
        assertEquals(
            CustomerDetailsResolver.ParsedPhone("RO", "722123456"),
            CustomerDetailsResolver.parsePhone("+40722123456"),
        )
        assertEquals("US", CustomerDetailsResolver.parsePhone("+14155550100")?.country)
        assertEquals("CA", CustomerDetailsResolver.parsePhone("+14155550100", "CA")?.country)
        assertEquals("GB", CustomerDetailsResolver.parsePhone("+442071838750")?.country)
        assertEquals("RU", CustomerDetailsResolver.parsePhone("+79001234567")?.country)
        assertNull(CustomerDetailsResolver.parsePhone("0722123456"))
        assertNull(CustomerDetailsResolver.parsePhone("+"))
        assertNull(CustomerDetailsResolver.parsePhone("+0000000"))
        assertNull(CustomerDetailsResolver.parsePhone(null))
    }

    @Test
    fun defaultsApplyOnlyToActiveBlocks() {
        val defaults = sampleDefaults()
        val all = CustomerDetailsVisibility.resolve(listOf("firstName", "email", "phone", "country"))
        val resolved = CustomerDetailsResolver.resolve(defaults, all)
        assertEquals("John Doe", resolved.fullName)
        assertEquals("john@doe.com", resolved.email)
        assertEquals("RO", resolved.phoneCountry)
        assertEquals("722123456", resolved.phoneNational)
        assertEquals("RO", resolved.country)
        assertEquals("Bucharest", resolved.city)
        assertEquals("010101", resolved.postalCode)
        assertEquals("Str. Example 1", resolved.address)
        assertEquals("", resolved.region)

        val contact = CustomerDetailsResolver.resolve(
            defaults,
            CustomerDetailsVisibility.resolve(listOf("firstName", "email")),
        )
        assertEquals("John Doe", contact.fullName)
        assertEquals("john@doe.com", contact.email)
        assertEquals("", contact.phoneCountry)
        assertEquals("", contact.country)
        assertEquals("", contact.city)

        assertEquals(
            CustomerDetailsInput(),
            CustomerDetailsResolver.resolve(defaults, CustomerDetailsVisibility.Inactive),
        )
        assertEquals(CustomerDetailsInput(), CustomerDetailsResolver.resolve(null, all))
    }

    @Test
    fun invalidCountryDropsRegionAndPostalButKeepsCityAndAddress() {
        val resolved = CustomerDetailsResolver.resolve(
            CustomerDetailsDefaults(
                phone = "+40722123456",
                country = "ZZ",
                region = "California",
                city = "Bucharest",
                postalCode = "010101",
                address = "Str. Example 1",
            ),
            CustomerDetailsVisibility.resolve(listOf("firstName", "email", "phone", "country")),
        )
        assertEquals("", resolved.country)
        assertEquals("", resolved.region)
        assertEquals("", resolved.postalCode)
        assertEquals("Bucharest", resolved.city)
        assertEquals("Str. Example 1", resolved.address)
        assertEquals("RO", resolved.phoneCountry)
    }

    @Test
    fun regionDefaultUsesTheCanonicalNameAndDropsUnknownValues() {
        val address = CustomerDetailsVisibility.resolve(listOf("country"))
        val known = CustomerDetailsResolver.resolve(
            CustomerDetailsDefaults(
                country = "us",
                region = "ca",
                city = "San Francisco",
                postalCode = "94105",
                address = "1 Market",
            ),
            address,
        )
        assertEquals("US", known.country)
        assertEquals("California", known.region)
        assertEquals("94105", known.postalCode)

        val unknown = CustomerDetailsResolver.resolve(
            CustomerDetailsDefaults(country = "US", region = "Atlantis", postalCode = "94105"),
            address,
        )
        assertEquals("US", unknown.country)
        assertEquals("", unknown.region)
        assertEquals("94105", unknown.postalCode)

        val noPostal = CustomerDetailsResolver.resolve(
            CustomerDetailsDefaults(country = "AE", region = "Dubai", postalCode = "00000", city = "Dubai"),
            address,
        )
        assertEquals("AE", noPostal.country)
        assertEquals("Dubai", noPostal.region)
        assertEquals("", noPostal.postalCode)
        assertEquals("Dubai", noPostal.city)
    }

    @Test
    fun unknownPhoneDialCodeIsDropped() {
        val resolved = CustomerDetailsResolver.resolve(
            CustomerDetailsDefaults(phone = "+0000000"),
            CustomerDetailsVisibility.resolve(listOf("phone")),
        )
        assertEquals("", resolved.phoneCountry)
        assertEquals("", resolved.phoneNational)
    }

    private fun sampleDefaults() = CustomerDetailsDefaults(
        fullName = " John Doe ",
        email = "john@doe.com",
        phone = "+40722123456",
        country = "ro",
        region = "Bucuresti",
        city = "Bucharest",
        postalCode = "010101",
        address = "Str. Example 1",
    )
}
