package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class PersonalField {
    FULL_NAME,
    EMAIL,
    PHONE_COUNTRY,
    PHONE,
    COUNTRY,
    REGION,
    ADDRESS,
    CITY,
    POSTAL,
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object CustomerDetailsValidator {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun activeFields(visibility: CustomerDetailsVisibility, countryCode: String?): List<PersonalField> {
        val names = mutableListOf<PersonalField>()
        if (visibility.showFullName) names += PersonalField.FULL_NAME
        if (visibility.showPhone) {
            names += PersonalField.PHONE_COUNTRY
            names += PersonalField.PHONE
        }
        if (visibility.showEmail) names += PersonalField.EMAIL
        if (visibility.showAddressSection) {
            names += PersonalField.COUNTRY
            val format = AddressFormats.forCountry(countryCode)
            if (format.showRegion) names += PersonalField.REGION
            names += PersonalField.ADDRESS
            names += PersonalField.CITY
            if (format.showPostal) names += PersonalField.POSTAL
        }
        return names
    }

    fun contactFields(visibility: CustomerDetailsVisibility): List<PersonalField> =
        activeFields(visibility, countryCode = null).filter { it in contactOrder }

    fun addressFields(visibility: CustomerDetailsVisibility, countryCode: String?): List<PersonalField> {
        if (!visibility.showAddressSection) return emptyList()
        val format = AddressFormats.forCountry(countryCode)
        val order = mutableListOf(PersonalField.COUNTRY)
        if (format.showRegion) order += PersonalField.REGION
        order += PersonalField.ADDRESS
        order += PersonalField.CITY
        if (format.showPostal) order += PersonalField.POSTAL
        return order
    }

    /** Returns a `personalDetails.errors.*` key, or null when the field is valid or inactive. */
    fun validate(
        field: PersonalField,
        input: CustomerDetailsInput,
        visibility: CustomerDetailsVisibility,
    ): String? {
        if (field !in activeFields(visibility, input.country)) return null
        return when (field) {
            PersonalField.FULL_NAME ->
                if (input.fullName.trim().isEmpty()) error("fullNameRequired") else null
            PersonalField.EMAIL -> validateEmail(input.email)
            PersonalField.PHONE_COUNTRY ->
                if (input.phoneCountry.trim().isEmpty()) error("phoneCountryRequired") else null
            PersonalField.PHONE -> validatePhone(input.phoneNational)
            PersonalField.COUNTRY ->
                if (input.country.trim().isEmpty()) error("countryRequired") else null
            PersonalField.REGION -> validateRegion(input)
            PersonalField.ADDRESS ->
                if (input.address.trim().isEmpty()) error("addressRequired") else null
            PersonalField.CITY ->
                if (input.city.trim().isEmpty()) error("cityRequired") else null
            PersonalField.POSTAL -> validatePostal(input)
        }
    }

    fun isValid(input: CustomerDetailsInput, visibility: CustomerDetailsVisibility): Boolean {
        if (!visibility.isActive) return true
        return activeFields(visibility, input.country).all { validate(it, input, visibility) == null }
    }

    fun errors(
        input: CustomerDetailsInput,
        visibility: CustomerDetailsVisibility,
    ): Map<PersonalField, String> {
        val result = linkedMapOf<PersonalField, String>()
        for (field in activeFields(visibility, input.country)) {
            val error = validate(field, input, visibility) ?: continue
            result[field] = error
        }
        return result
    }

    private fun validateEmail(email: String): String? {
        val value = email.trim()
        if (value.isEmpty()) return error("emailRequired")
        if (!emailPattern.matches(value)) return error("emailInvalid")
        return null
    }

    private fun validatePhone(national: String): String? {
        if (national.trim().isEmpty()) return error("phoneRequired")
        if (!PhoneNumber.isValid(national)) return error("phoneInvalid")
        return null
    }

    private fun validateRegion(input: CustomerDetailsInput): String? {
        val format = AddressFormats.forCountry(input.country)
        if (!format.showRegion) return null
        val required = AddressFormats.regionKeys(format.regionType).requiredError
        if (!Subdivisions.isSelectionComplete(input.country, input.region)) return error(required)
        return null
    }

    private fun validatePostal(input: CustomerDetailsInput): String? {
        val format = AddressFormats.forCountry(input.country)
        if (!format.showPostal) return null
        val keys = AddressFormats.postalKeys(format.postalType)
        val value = input.postalCode.trim()
        if (value.isEmpty()) return error(keys.requiredError)
        if (!AddressFormats.isPostalCodeValid(value, format)) return error(keys.invalidError)
        return null
    }

    private fun error(code: String) = "personalDetails.errors.$code"

    private val contactOrder = setOf(
        PersonalField.FULL_NAME,
        PersonalField.PHONE_COUNTRY,
        PersonalField.PHONE,
        PersonalField.EMAIL,
    )
}
