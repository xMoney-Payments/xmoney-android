package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo
import org.json.JSONObject

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class CustomerDetailsInput(
    val fullName: String = "",
    val email: String = "",
    val phoneCountry: String = "",
    val phoneNational: String = "",
    val country: String = "",
    val region: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class WalletContact(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val countryCode: String = "",
) {
    fun hasData(): Boolean {
        if (firstName.isNotBlank() || lastName.isNotBlank() || email.isNotBlank() || phone.isNotBlank()) {
            return true
        }
        return line1.isNotBlank() || line2.isNotBlank() || city.isNotBlank() ||
            state.isNotBlank() || postalCode.isNotBlank() || countryCode.isNotBlank()
    }

    companion object {
        fun fromGooglePay(json: JSONObject): WalletContact? {
            val billing = json.optJSONObject("paymentMethodData")
                ?.optJSONObject("info")
                ?.optJSONObject("billingAddress")
            val email = json.optString("email")
            if (billing == null && email.isBlank()) return null
            val (firstName, lastName) = CustomerDetails.splitFullName(billing?.optString("name").orEmpty())
            val contact = WalletContact(
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = billing?.optString("phoneNumber").orEmpty(),
                line1 = billing?.optString("address1").orEmpty(),
                line2 = billing?.optString("address2").orEmpty(),
                city = billing?.optString("locality").orEmpty(),
                state = billing?.optString("administrativeArea").orEmpty(),
                postalCode = billing?.optString("postalCode").orEmpty(),
                countryCode = billing?.optString("countryCode").orEmpty(),
            )
            return contact.takeIf { it.hasData() }
        }
    }
}

/**
 * Billing fields requested from Google Pay when customer details collection is on.
 * Missing wallet fields never block the payment.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class GooglePayBillingParameters(
    val emailRequired: Boolean = false,
    val billingAddressRequired: Boolean = false,
    val phoneNumberRequired: Boolean = false,
    val billingAddressFormat: String = "MIN",
) {
    companion object {
        val None = GooglePayBillingParameters()

        fun resolve(enabled: Boolean, visibility: CustomerDetailsVisibility): GooglePayBillingParameters {
            if (!enabled || !visibility.isActive) return None
            val billingAddressRequired = visibility.showFullName ||
                visibility.showPhone ||
                visibility.showAddressSection
            return GooglePayBillingParameters(
                emailRequired = visibility.showEmail,
                billingAddressRequired = billingAddressRequired,
                phoneNumberRequired = visibility.showPhone,
                billingAddressFormat = if (visibility.showAddressSection) "FULL" else "MIN",
            )
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object CustomerDetails {
    private val payloadKeys = listOf(
        "fullName",
        "country",
        "region",
        "city",
        "postalCode",
        "address",
        "phone",
        "email",
    )

    /**
     * Splits on the first space, matching `splitFullName` in the checkout SDK.
     * A one-word name is valid: `lastName` is sent as `""` and must not be required.
     */
    fun splitFullName(name: String?): Pair<String, String> {
        val trimmed = name.orEmpty().trim()
        if (trimmed.isEmpty()) return "" to ""
        val space = trimmed.indexOf(' ')
        if (space == -1) return trimmed to ""
        return trimmed.substring(0, space) to trimmed.substring(space + 1).trim()
    }

    fun buildPhone(countryCode: String?, national: String?): String {
        val dialCode = Countries.byCode(countryCode)?.dialCode ?: return ""
        val digits = PhoneNumber.formatForPayload(PhoneNumber.normalizeDigits(national))
        if (dialCode.isEmpty() || digits.isEmpty()) return ""
        return "+$dialCode$digits"
    }

    fun apiPayloadFromForm(
        input: CustomerDetailsInput,
        visibility: CustomerDetailsVisibility,
    ): Map<String, String>? {
        if (!visibility.isActive) return null
        val draft = linkedMapOf<String, String>()
        if (visibility.showFullName) putTrimmed(draft, "fullName", input.fullName)
        if (visibility.showEmail) putTrimmed(draft, "email", input.email)
        if (visibility.showPhone) {
            val phone = buildPhone(input.phoneCountry, input.phoneNational)
            if (phone.isNotEmpty()) draft["phone"] = phone
        }
        if (visibility.showAddressSection) {
            putTrimmed(draft, "country", input.country)
            val region = Subdivisions.resolveForPayload(input.country, input.region)
            if (!region.isNullOrBlank()) draft["region"] = region
            putTrimmed(draft, "city", input.city)
            putTrimmed(draft, "postalCode", input.postalCode)
            putTrimmed(draft, "address", input.address)
        }
        return toApiPayload(draft)
    }

    fun apiPayloadFromWallet(
        contact: WalletContact?,
        enabled: Boolean,
        visibility: CustomerDetailsVisibility,
    ): Map<String, String>? {
        if (!enabled || !visibility.isActive) return null
        val internal = walletToInternal(contact) ?: return null
        return toApiPayload(prune(internal, visibility))
    }

    fun toApiPayload(internal: Map<String, String>?): Map<String, String>? {
        val payload = buildPayload(internal) ?: return null
        val api = linkedMapOf<String, String>()
        val (firstName, lastName) = splitFullName(payload["fullName"])
        if (firstName.isNotEmpty()) {
            api["firstName"] = firstName
            api["lastName"] = lastName
        }
        payload["country"]?.let { api["country"] = it }
        payload["city"]?.let { api["city"] = it }
        payload["postalCode"]?.let { api["zipCode"] = it }
        val street = payload["address"].orEmpty()
        val region = payload["region"].orEmpty()
        val address = when {
            region.isNotEmpty() && street.isNotEmpty() -> "$street, $region"
            region.isNotEmpty() -> region
            else -> street
        }
        if (address.isNotEmpty()) api["address"] = address
        payload["phone"]?.let { api["phone"] = it }
        payload["email"]?.let { api["email"] = it }
        return api.ifEmpty { null }
    }

    /**
     * Given and family names are joined, then split again on the first space.
     * The phone is the wallet string, not `+{dial}{national}`.
     * A known region becomes its canonical name. Anything else is kept raw.
     */
    fun walletToInternal(contact: WalletContact?): Map<String, String>? {
        if (contact == null || !contact.hasData()) return null
        val fullName = listOf(contact.firstName, contact.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val street = listOf(contact.line1, contact.line2)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        val region = Subdivisions.normalizeAdministrativeArea(contact.countryCode, contact.state)
            ?: contact.state.trim()
        return buildPayload(
            mapOf(
                "fullName" to fullName,
                "email" to contact.email,
                "phone" to contact.phone,
                "country" to contact.countryCode,
                "region" to region.orEmpty(),
                "city" to contact.city,
                "postalCode" to contact.postalCode,
                "address" to street,
            ),
        )
    }

    fun prune(details: Map<String, String>?, visibility: CustomerDetailsVisibility): Map<String, String>? {
        if (details == null || !visibility.isActive) return null
        val pruned = linkedMapOf<String, String>()
        if (visibility.showFullName) details["fullName"]?.let { pruned["fullName"] = it }
        if (visibility.showEmail) details["email"]?.let { pruned["email"] = it }
        if (visibility.showPhone) details["phone"]?.let { pruned["phone"] = it }
        if (visibility.showAddressSection) {
            for (key in listOf("country", "region", "city", "postalCode", "address")) {
                details[key]?.let { pruned[key] = it }
            }
        }
        return buildPayload(pruned)
    }

    fun toJson(payload: Map<String, String>): String = JSONObject(payload).toString()

    private fun buildPayload(details: Map<String, String>?): Map<String, String>? {
        if (details == null) return null
        val payload = linkedMapOf<String, String>()
        for (key in payloadKeys) {
            val value = details[key]?.trim().orEmpty()
            if (value.isNotEmpty()) payload[key] = value
        }
        return payload.ifEmpty { null }
    }

    private fun putTrimmed(target: MutableMap<String, String>, key: String, value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isNotEmpty()) target[key] = trimmed
    }
}
