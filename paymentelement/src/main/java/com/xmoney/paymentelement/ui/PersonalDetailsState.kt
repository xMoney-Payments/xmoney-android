package com.xmoney.paymentelement.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xmoney.payments.config.CardValidationDisplay
import com.xmoney.payments.config.Strings
import com.xmoney.payments.config.ValidationMode
import com.xmoney.payments.customerdetails.AddressFormats
import com.xmoney.payments.customerdetails.CustomerDetails
import com.xmoney.payments.customerdetails.CustomerDetailsInput
import com.xmoney.payments.customerdetails.CustomerDetailsValidator
import com.xmoney.payments.customerdetails.CustomerDetailsVisibility
import com.xmoney.payments.customerdetails.PersonalField

internal class PersonalDetailsFormState(
    val visibility: CustomerDetailsVisibility,
    private val validationMode: ValidationMode,
) {
    var input by mutableStateOf(CustomerDetailsInput())
        private set
    var completed by mutableStateOf(false)
        private set
    var showAllErrors by mutableStateOf(false)
        private set

    val touched = mutableStateMapOf<PersonalField, Boolean>()
    val focused = mutableStateMapOf<PersonalField, Boolean>()
    private val displayedErrors = mutableStateMapOf<PersonalField, String?>()
    private val forceShown = mutableStateMapOf<PersonalField, Boolean>()

    fun errorMessage(field: PersonalField, locale: String): String? {
        if (!shouldShow(field)) return null
        val key = displayedErrors[field] ?: return null
        return Strings.text(key, locale)
    }

    fun shouldShow(field: PersonalField): Boolean {
        if (displayedErrors[field] == null) return false
        if (forceShown[field] == true) return true
        return CardValidationDisplay.shouldShowError(
            validationMode,
            touched[field] == true,
            showAllErrors,
        )
    }

    fun onTextChange(field: PersonalField, value: String) {
        input = when (field) {
            PersonalField.FULL_NAME -> input.copy(fullName = value)
            PersonalField.EMAIL -> input.copy(email = value)
            PersonalField.PHONE -> input.copy(phoneNational = value)
            PersonalField.ADDRESS -> input.copy(address = value)
            PersonalField.CITY -> input.copy(city = value)
            PersonalField.POSTAL -> input.copy(postalCode = value)
            else -> input
        }
        refresh(Trigger.CHANGE)
    }

    fun onBlur(field: PersonalField) {
        touched[field] = true
        refresh(Trigger.BLUR, field)
    }

    fun onPhoneCountrySelected(code: String) {
        input = input.copy(phoneCountry = code)
        touched[PersonalField.PHONE_COUNTRY] = true
        refresh(Trigger.BLUR, PersonalField.PHONE_COUNTRY)
    }

    fun onRegionSelected(name: String) {
        input = input.copy(region = name)
        touched[PersonalField.REGION] = true
        forceShown[PersonalField.REGION] = false
        refresh(Trigger.BLUR, PersonalField.REGION)
    }

    fun onCountrySelected(code: String) {
        val hadRegion = input.region.isNotBlank()
        val hadPostal = input.postalCode.isNotBlank()
        val regionTouched = touched[PersonalField.REGION] == true
        val postalTouched = touched[PersonalField.POSTAL] == true
        val format = AddressFormats.forCountry(code)
        input = input.copy(
            country = code,
            region = "",
            postalCode = if (format.showPostal) input.postalCode else "",
        )
        touched[PersonalField.COUNTRY] = true
        displayedErrors[PersonalField.REGION] = null
        displayedErrors[PersonalField.POSTAL] = null
        forceShown[PersonalField.REGION] = false
        forceShown[PersonalField.POSTAL] = false
        if (!format.showPostal) touched[PersonalField.POSTAL] = false
        refresh(Trigger.BLUR, PersonalField.COUNTRY)
        if (hadRegion || regionTouched) {
            touched[PersonalField.REGION] = true
            forceShown[PersonalField.REGION] = true
            displayedErrors[PersonalField.REGION] =
                CustomerDetailsValidator.validate(PersonalField.REGION, input, visibility)
        }
        if ((hadPostal || postalTouched) && format.showPostal) {
            touched[PersonalField.POSTAL] = true
            forceShown[PersonalField.POSTAL] = true
            displayedErrors[PersonalField.POSTAL] =
                CustomerDetailsValidator.validate(PersonalField.POSTAL, input, visibility)
        }
    }

    fun restore(saved: CustomerDetailsInput, wasCompleted: Boolean) {
        input = saved
        completed = wasCompleted && CustomerDetailsValidator.isValid(saved, visibility)
    }

    fun confirm(): Boolean {
        showAllErrors = true
        val fields = CustomerDetailsValidator.activeFields(visibility, input.country)
        fields.forEach { touched[it] = true }
        refresh(Trigger.SUBMIT)
        val valid = CustomerDetailsValidator.isValid(input, visibility)
        if (valid) completed = true
        return valid
    }

    fun onLeave() {
        if (!completed) return
        completed = CustomerDetailsValidator.isValid(input, visibility)
    }

    fun payload(): Map<String, String>? =
        if (completed) CustomerDetails.apiPayloadFromForm(input, visibility) else null

    private fun refresh(trigger: Trigger, field: PersonalField? = null) {
        val current = CustomerDetailsValidator.errors(input, visibility)
        val fields = CustomerDetailsValidator.activeFields(visibility, input.country)
        when (trigger) {
            Trigger.SUBMIT -> fields.forEach { displayedErrors[it] = current[it] }
            Trigger.BLUR -> if (field != null) displayedErrors[field] = current[field]
            Trigger.CHANGE -> fields.forEach {
                if (CardValidationDisplay.shouldRefreshDisplayedErrorOnChange(
                        validationMode,
                        touched[it] == true,
                        showAllErrors,
                    )
                ) {
                    displayedErrors[it] = current[it]
                    if (current[it] == null) forceShown[it] = false
                }
            }
        }
    }

    private enum class Trigger { CHANGE, BLUR, SUBMIT }
}
