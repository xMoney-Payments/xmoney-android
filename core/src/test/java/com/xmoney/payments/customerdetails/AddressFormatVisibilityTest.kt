package com.xmoney.payments.customerdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressFormatVisibilityTest {
    @Test
    fun hidesRegionAndPostalUntilACountryIsSelected() {
        val format = AddressFormats.forCountry("")
        assertFalse(format.showRegion)
        assertFalse(format.showPostal)
        assertEquals(null, format.regionType)
    }

    @Test
    fun derivesShowPostalAndShowRegionForEveryCheckoutCountry() {
        for (country in Countries.all()) {
            val format = AddressFormats.forCountry(country.code)
            val expectedPostal = AddressFormats.countryUsesPostalCode(country.code)
            val expectedRegionType = AddressFormats.regionTypeForCountry(country.code)
            assertEquals(country.code, expectedPostal, format.showPostal)
            assertEquals(country.code, expectedRegionType != null, format.showRegion)
            assertEquals(country.code, expectedRegionType, format.regionType)
            assertEquals(country.code, expectedRegionType != null, AddressFormats.countryUsesRegion(country.code))
        }
    }

    @Test
    fun mapsRegionTypesToI18nKeys() {
        assertEquals("personalDetails.region.state", AddressFormats.regionKeys("state").label)
        assertEquals("personalDetails.region.stateSelect", AddressFormats.regionKeys("state").selectPlaceholder)
        assertEquals("provinceRequired", AddressFormats.regionKeys("province").requiredError)
        assertEquals("personalDetails.region.generic", AddressFormats.regionKeys(null).label)
    }

    @Test
    fun usesStateLabelsForTheUnitedStatesAndProvinceLabelsForCanada() {
        assertEquals("state", AddressFormats.regionTypeForCountry("US"))
        assertEquals("province", AddressFormats.regionTypeForCountry("CA"))
        assertTrue(AddressFormats.forCountry("US").showRegion)
        assertFalse(AddressFormats.forCountry("RO").showRegion)
        val emirates = AddressFormats.forCountry("AE")
        assertFalse(emirates.showPostal)
        assertTrue(emirates.showRegion)
        assertEquals("emirate", emirates.regionType)
    }

    @Test
    fun postalPatternsMatchCheckoutOverrides() {
        assertTrue(AddressFormats.isPostalCodeValid("94105", AddressFormats.forCountry("US")))
        assertTrue(AddressFormats.isPostalCodeValid("94105-1234", AddressFormats.forCountry("US")))
        assertFalse(AddressFormats.isPostalCodeValid("9410", AddressFormats.forCountry("US")))
        assertTrue(AddressFormats.isPostalCodeValid("010011", AddressFormats.forCountry("RO")))
        assertFalse(AddressFormats.isPostalCodeValid("123", AddressFormats.forCountry("RO")))
        assertTrue(AddressFormats.isPostalCodeValid("SW1A 1AA", AddressFormats.forCountry("GB")))
        assertTrue(AddressFormats.isPostalCodeValid("1010", AddressFormats.forCountry("AT")))
    }
}
