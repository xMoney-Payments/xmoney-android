package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo

internal data class PostalOverride(
    val postalType: String,
    val pattern: Regex,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class AddressFormat(
    val postalType: String,
    val postalPattern: Regex,
    val showRegion: Boolean,
    val showPostal: Boolean,
    val regionType: String?,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class PostalI18nKeys(
    val label: String,
    val placeholder: String,
    val requiredError: String,
    val invalidError: String,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class RegionI18nKeys(
    val label: String,
    val placeholder: String,
    val selectPlaceholder: String,
    val requiredError: String,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object AddressFormats {
    fun forCountry(countryCode: String?): AddressFormat {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) {
            return AddressFormat(
                postalType = AddressFormatData.DEFAULT_POSTAL_TYPE,
                postalPattern = AddressFormatData.defaultPostalPattern,
                showRegion = false,
                showPostal = false,
                regionType = null,
            )
        }
        val override = AddressFormatData.countryOverrides[code]
        val regionType = AddressFormatData.regionTypeByCountry[code]
        return AddressFormat(
            postalType = override?.postalType ?: AddressFormatData.DEFAULT_POSTAL_TYPE,
            postalPattern = override?.pattern ?: AddressFormatData.defaultPostalPattern,
            showRegion = regionType != null,
            showPostal = code !in AddressFormatData.noPostalCountries,
            regionType = regionType,
        )
    }

    fun regionTypeForCountry(countryCode: String?): String? {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) return null
        return AddressFormatData.regionTypeByCountry[code]
    }

    fun countryUsesPostalCode(countryCode: String?): Boolean {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) return false
        return code !in AddressFormatData.noPostalCountries
    }

    fun countryUsesRegion(countryCode: String?): Boolean = regionTypeForCountry(countryCode) != null

    fun postalKeys(postalType: String?): PostalI18nKeys {
        val type = postalType ?: AddressFormatData.DEFAULT_POSTAL_TYPE
        return PostalI18nKeys(
            label = "personalDetails.postal.$type",
            placeholder = "personalDetails.postal.${type}Placeholder",
            requiredError = "${type}Required",
            invalidError = "${type}Invalid",
        )
    }

    fun regionKeys(regionType: String?): RegionI18nKeys {
        if (regionType == null) {
            return RegionI18nKeys(
                label = "personalDetails.region.generic",
                placeholder = "personalDetails.region.genericPlaceholder",
                selectPlaceholder = "personalDetails.region.genericSelect",
                requiredError = "regionRequired",
            )
        }
        return RegionI18nKeys(
            label = "personalDetails.region.$regionType",
            placeholder = "personalDetails.region.${regionType}Placeholder",
            selectPlaceholder = "personalDetails.region.${regionType}Select",
            requiredError = "${regionType}Required",
        )
    }

    fun isPostalCodeValid(value: String?, format: AddressFormat): Boolean {
        if (!format.showPostal) return true
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return format.postalPattern.matches(normalized)
    }
}
