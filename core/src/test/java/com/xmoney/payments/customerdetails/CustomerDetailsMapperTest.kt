package com.xmoney.payments.customerdetails

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerDetailsMapperTest {
    @Test
    fun appendsPlainUsRegionToAddressAndOmitsState() {
        val api = CustomerDetails.toApiPayload(
            mapOf(
                "fullName" to "Jane Doe",
                "country" to "US",
                "region" to "California",
                "address" to "1 Market St",
                "city" to "San Francisco",
                "postalCode" to "94105",
            ),
        )!!
        assertEquals("1 Market St, California", api["address"])
        assertFalse(api.containsKey("state"))
        assertEquals("94105", api["zipCode"])
        assertEquals("Jane", api["firstName"])
        assertEquals("Doe", api["lastName"])
    }

    @Test
    fun oneWordNameSendsEmptyLastName() {
        // A one-word name is valid. lastName stays "" and is not required.
        val api = CustomerDetails.toApiPayload(mapOf("fullName" to "Jane"))!!
        assertEquals("Jane", api["firstName"])
        assertEquals("", api["lastName"])
        val json = JSONObject(CustomerDetails.toJson(api))
        assertEquals("", json.getString("lastName"))
    }

    @Test
    fun omitsRegionWhenSelectionIsEmpty() {
        val api = CustomerDetails.toApiPayload(
            mapOf(
                "country" to "US",
                "address" to "1 Market St",
            ),
        )!!
        assertFalse(api.containsKey("state"))
        assertEquals("1 Market St", api["address"])
    }

    @Test
    fun mapsWalletCaliforniaAndCodeToPlainRegion() {
        for (state in listOf("California", "CA")) {
            val internal = CustomerDetails.walletToInternal(
                WalletContact(
                    firstName = "Jane",
                    lastName = "Doe",
                    line1 = "1 Market St",
                    countryCode = "US",
                    state = state,
                    city = "San Francisco",
                    postalCode = "94105",
                ),
            )!!
            assertEquals("California", internal["region"])
            val api = CustomerDetails.toApiPayload(internal)!!
            assertEquals("1 Market St, California", api["address"])
            assertFalse(api.containsKey("state"))
        }
    }

    @Test
    fun appendsMexicoRegionUsingPlainName() {
        val region = Subdivisions.resolveForPayload("MX", "Aguascalientes")
        assertEquals("Aguascalientes", region)
        val api = CustomerDetails.toApiPayload(
            mapOf(
                "country" to "MX",
                "region" to region!!,
                "address" to "Calle 1",
            ),
        )!!
        assertEquals("Calle 1, Aguascalientes", api["address"])
    }

    @Test
    fun mapsWalletMxAlias() {
        val internal = CustomerDetails.walletToInternal(
            WalletContact(
                firstName = "Jane",
                lastName = "Doe",
                line1 = "Main",
                countryCode = "MX",
                state = "AGS.",
                city = "Aguascalientes",
            ),
        )!!
        assertEquals("Aguascalientes", internal["region"])
    }

    @Test
    fun keepsUnmatchedWalletAdministrativeAreaRaw() {
        val internal = CustomerDetails.walletToInternal(
            WalletContact(
                line1 = "1600 Pennsylvania Ave",
                city = "Washington",
                state = "D.C.",
                countryCode = "US",
            ),
        )!!
        assertEquals("D.C.", internal["region"])
        val api = CustomerDetails.toApiPayload(internal)!!
        assertEquals("1600 Pennsylvania Ave, D.C.", api["address"])
        assertFalse(api.containsKey("state"))
    }

    @Test
    fun normalizesAdministrativeAreaByNameOrAlias() {
        assertEquals("California", Subdivisions.normalizeAdministrativeArea("US", "California"))
        assertEquals("California", Subdivisions.normalizeAdministrativeArea("US", "CA"))
        assertEquals("New South Wales", Subdivisions.normalizeAdministrativeArea("AU", "NSW"))
        assertNull(Subdivisions.normalizeAdministrativeArea("US", "Not A State"))
    }

    @Test
    fun phoneDropsOneLeadingZero() {
        assertEquals("+40722111222", CustomerDetails.buildPhone("RO", "0722 111 222"))
        assertEquals("+400722", CustomerDetails.buildPhone("RO", "0722"))
    }

    @Test
    fun formPayloadPrunesToVisibleFields() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val api = CustomerDetails.apiPayloadFromForm(
            CustomerDetailsInput(fullName = "Jane Doe", email = "a@b.co", country = "RO"),
            visibility,
        )!!
        assertEquals("a@b.co", api["email"])
        assertFalse(api.containsKey("firstName"))
        assertFalse(api.containsKey("country"))
    }

    @Test
    fun walletPayloadIsPrunedAndDoesNotBlockWhenContactIsMissing() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("firstName", "email", "address"))
        assertNull(
            CustomerDetails.apiPayloadFromWallet(
                contact = null,
                enabled = true,
                visibility = visibility,
            ),
        )
        val api = CustomerDetails.apiPayloadFromWallet(
            WalletContact(email = "a@b.co", line1 = "Main", countryCode = "RO", city = "Cluj"),
            enabled = true,
            visibility = visibility,
        )!!
        assertEquals("a@b.co", api["email"])
        assertEquals("Main", api["address"])
        assertEquals("RO", api["country"])
        assertEquals("Cluj", api["city"])
        assertFalse(api.containsKey("firstName"))
    }

    @Test
    fun googlePayJsonMapsBillingContact() {
        val json = JSONObject(
            """
            {
              "email": "a@b.co",
              "paymentMethodData": {
                "info": {
                  "billingAddress": {
                    "name": "Jane Doe",
                    "address1": "1 Market St",
                    "address2": "Suite 2",
                    "locality": "San Francisco",
                    "administrativeArea": "CA",
                    "postalCode": "94105",
                    "countryCode": "US",
                    "phoneNumber": "+14155550123"
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val contact = WalletContact.fromGooglePay(json)!!
        assertEquals("Jane", contact.firstName)
        assertEquals("Doe", contact.lastName)
        assertEquals("a@b.co", contact.email)
        assertEquals("+14155550123", contact.phone)
        assertEquals("CA", contact.state)
        val api = CustomerDetails.apiPayloadFromWallet(
            contact,
            enabled = true,
            visibility = CustomerDetailsVisibility.resolve(
                listOf("firstName", "email", "phone", "address", "city", "zipCode", "country", "state"),
            ),
        )!!
        assertEquals("1 Market St, Suite 2, California", api["address"])
        assertEquals("94105", api["zipCode"])
        assertEquals("+14155550123", api["phone"])
    }
}
