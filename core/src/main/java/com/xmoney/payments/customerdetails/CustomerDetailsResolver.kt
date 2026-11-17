package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo
import com.xmoney.payments.config.CustomerDetailsDefaults
import com.xmoney.payments.config.CustomerDetailsFields

/**
 * Turns `customerDetails.fields` and `defaultValues` into the form the card UI already uses.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object CustomerDetailsResolver {
    private val addressFields = listOf("country", "state", "city", "zipCode", "address")
    private const val MAX_DIAL_CODE_LENGTH = 6

    /** Used when several countries share a calling code and no country default narrows it. */
    private val preferredCountryByDialCode = mapOf(
        "1" to "US",
        "7" to "RU",
        "44" to "GB",
    )

    data class ParsedPhone(
        val country: String,
        val national: String,
    )

    /** Field names [CustomerDetailsVisibility.resolve] already understands. */
    fun inlineCheckoutFields(fields: CustomerDetailsFields): List<String> {
        val resolved = mutableListOf<String>()
        if (fields.fullName) resolved += listOf("firstName", "lastName")
        if (fields.email) resolved += "email"
        if (fields.phone) resolved += "phone"
        if (fields.address) resolved += addressFields
        return resolved
    }

    /**
     * Keeps only defaults that can be shown on the card form.
     * Anything else is dropped. Values are not logged.
     */
    fun resolve(
        defaults: CustomerDetailsDefaults?,
        visibility: CustomerDetailsVisibility,
    ): CustomerDetailsInput {
        if (defaults == null || !visibility.isActive) return CustomerDetailsInput()

        var resolved = CustomerDetailsInput()
        if (visibility.showFullName) {
            val fullName = text(defaults.fullName)
            if (fullName.isNotEmpty()) resolved = resolved.copy(fullName = fullName)
        }
        if (visibility.showEmail) {
            val email = text(defaults.email)
            if (email.isNotEmpty()) resolved = resolved.copy(email = email)
        }

        val country = if (visibility.showAddressSection) countryCode(defaults.country) else ""
        if (visibility.showPhone) {
            val phone = parsePhone(defaults.phone, country)
            if (phone != null) {
                resolved = resolved.copy(phoneCountry = phone.country, phoneNational = phone.national)
            }
        }
        if (visibility.showAddressSection) {
            val city = text(defaults.city)
            val address = text(defaults.address)
            if (city.isNotEmpty()) resolved = resolved.copy(city = city)
            if (address.isNotEmpty()) resolved = resolved.copy(address = address)
            if (country.isNotEmpty()) {
                resolved = resolved.copy(country = country)
                val format = AddressFormats.forCountry(country)
                if (format.showRegion) {
                    val region = Subdivisions.find(country, defaults.region)?.name
                    if (region != null) resolved = resolved.copy(region = region)
                }
                val postalCode = text(defaults.postalCode)
                if (format.showPostal && postalCode.isNotEmpty()) {
                    resolved = resolved.copy(postalCode = postalCode)
                }
            }
        }
        return resolved
    }

    /**
     * Splits an E.164 number (`+40722123456`) into a phone country and national digits.
     * Returns null when the value does not start with `+` or the dial code matches no country.
     */
    fun parsePhone(value: String?, preferredCountryCode: String = ""): ParsedPhone? {
        val raw = text(value)
        if (!raw.startsWith("+")) return null
        val digits = raw.filter { it in '0'..'9' }
        if (digits.length < 2) return null

        val maxLength = minOf(MAX_DIAL_CODE_LENGTH, digits.length - 1)
        for (length in maxLength downTo 1) {
            val dial = digits.substring(0, length)
            val candidates = Countries.all().filter { it.dialCode == dial }
            val match = candidates.firstOrNull { it.code == preferredCountryCode }
                ?: candidates.firstOrNull { it.code == preferredCountryByDialCode[dial] }
                ?: candidates.firstOrNull()
                ?: continue
            val national = digits.substring(length)
            if (national.isEmpty()) continue
            return ParsedPhone(match.code, national)
        }
        return null
    }

    private fun text(value: String?): String = value?.trim().orEmpty()

    private fun countryCode(value: String?): String {
        val code = text(value).uppercase()
        if (code.isEmpty() || Countries.byCode(code) == null) return ""
        return code
    }
}
