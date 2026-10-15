package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo
import java.text.Collator
import java.util.Locale

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class Country(
    val code: String,
    val name: String,
    val dialCode: String,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object Countries {
    fun all(): List<Country> = CountriesData.all

    fun byCode(code: String?): Country? {
        val normalized = code?.trim()?.uppercase().orEmpty()
        if (normalized.isEmpty()) return null
        return CountriesData.all.find { it.code == normalized }
    }

    fun localizedName(country: Country, locale: Locale): String {
        if (locale.language.equals("en", ignoreCase = true)) return country.name
        val display = Locale("", country.code).getDisplayCountry(locale)
        if (display.isBlank() || display.equals(country.code, ignoreCase = true)) return country.name
        return display
    }

    fun sorted(locale: Locale): List<Country> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return CountriesData.all.sortedWith { left, right ->
            collator.compare(localizedName(left, locale), localizedName(right, locale))
        }
    }

    fun flagEmoji(code: String): String {
        if (code.length != 2) return ""
        return buildString {
            for (char in code.uppercase()) {
                append(Character.toChars(127397 + char.code))
            }
        }
    }

    fun phoneCompact(country: Country): String =
        "${flagEmoji(country.code)} +${country.dialCode}"

    fun phoneExpanded(country: Country, locale: Locale): String =
        "${localizedName(country, locale)} ${flagEmoji(country.code)}  +${country.dialCode}"

    fun addressCompact(country: Country, locale: Locale): String =
        "${flagEmoji(country.code)} ${localizedName(country, locale)}"
}
