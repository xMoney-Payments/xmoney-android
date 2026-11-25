package com.xmoney.payments.customerdetails

import androidx.annotation.RestrictTo

/**
 * Field list from /inline-checkout/fields.
 * firstName or lastName shows one full-name input.
 * Any address field shows the whole address block.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class CustomerDetailsVisibility(
    val showFullName: Boolean = false,
    val showEmail: Boolean = false,
    val showPhone: Boolean = false,
    val showAddressSection: Boolean = false,
) {
    val isActive: Boolean
        get() = showFullName || showEmail || showPhone || showAddressSection

    companion object {
        val Inactive = CustomerDetailsVisibility()

        private val addressFields = setOf("country", "state", "city", "zipCode", "address")

        fun resolve(fields: List<String>?): CustomerDetailsVisibility {
            val set = fields.orEmpty().toSet()
            return CustomerDetailsVisibility(
                showFullName = "firstName" in set || "lastName" in set,
                showEmail = "email" in set,
                showPhone = "phone" in set,
                showAddressSection = set.any { it in addressFields },
            )
        }
    }
}
