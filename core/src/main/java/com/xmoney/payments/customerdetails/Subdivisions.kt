package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class Subdivision(
    val name: String,
    val aliases: List<String> = emptyList(),
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object Subdivisions {
    fun forCountry(countryCode: String?): List<Subdivision> {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) return emptyList()
        return SubdivisionsData.byCountry[code].orEmpty()
    }

    fun find(countryCode: String?, value: String?): Subdivision? {
        val needle = value?.trim().orEmpty()
        if (needle.isEmpty()) return null
        val lower = needle.lowercase()
        for (entry in forCountry(countryCode)) {
            if (entry.name.lowercase() == lower) return entry
            if (entry.aliases.any { it.lowercase() == lower }) return entry
        }
        return null
    }

    fun normalizeAdministrativeArea(countryCode: String?, administrativeArea: String?): String? =
        find(countryCode, administrativeArea)?.name

    fun resolveForPayload(countryCode: String?, regionValue: String?): String? =
        find(countryCode, regionValue)?.name

    fun isSelectionComplete(countryCode: String?, regionValue: String?): Boolean =
        resolveForPayload(countryCode, regionValue) != null
}
