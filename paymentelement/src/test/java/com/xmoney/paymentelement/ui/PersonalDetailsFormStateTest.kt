package com.xmoney.paymentelement.ui

import com.xmoney.payments.config.ValidationMode
import com.xmoney.payments.customerdetails.CustomerDetailsInput
import com.xmoney.payments.customerdetails.CustomerDetailsVisibility
import com.xmoney.payments.customerdetails.PersonalField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDetailsFormStateTest {
    @Test
    fun dismissMarksTheFieldTouchedAndShowsTheRequiredError() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        assertNull(state.errorMessage(PersonalField.EMAIL, "en"))
        state.onBlur(PersonalField.EMAIL)
        assertEquals("Email is required.", state.errorMessage(PersonalField.EMAIL, "en"))
    }

    @Test
    fun confirmWithAnEmptyFieldStaysIncompleteAndShowsTheError() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_SUBMIT)
        assertFalse(state.confirm())
        assertFalse(state.completed)
        assertEquals("Email is required.", state.errorMessage(PersonalField.EMAIL, "en"))
    }

    @Test
    fun backClearsAPreviouslyCompletedEditWhenTheValuesAreInvalid() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        state.onTextChange(PersonalField.EMAIL, "a@b.co")
        assertTrue(state.confirm())
        assertTrue(state.completed)
        state.onTextChange(PersonalField.EMAIL, "not-an-email")
        assertTrue(state.completed)
        state.onLeave()
        assertFalse(state.completed)
    }

    @Test
    fun backKeepsAPreviouslyCompletedEditWhenTheValuesAreValidAgain() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        state.onTextChange(PersonalField.EMAIL, "a@b.co")
        assertTrue(state.confirm())
        state.onTextChange(PersonalField.EMAIL, "not-an-email")
        state.onTextChange(PersonalField.EMAIL, "ada@example.com")
        state.onLeave()
        assertTrue(state.completed)
        assertEquals("ada@example.com", state.payload()?.get("email"))
    }

    @Test
    fun restoreKeepsACompletedDraft() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        state.restore(
            com.xmoney.payments.customerdetails.CustomerDetailsInput(email = "ada@example.com"),
            wasCompleted = true,
        )
        assertTrue(state.completed)
        assertEquals("ada@example.com", state.payload()?.get("email"))
    }

    @Test
    fun hostConfirmPaysAValidOpenPageAndStaysWhenInvalid() {
        assertFalse(hostConfirmPays(detailsPageOpen = true, detailsValid = false))
        assertTrue(hostConfirmPays(detailsPageOpen = true, detailsValid = true))
        assertTrue(hostConfirmPays(detailsPageOpen = false, detailsValid = false))
    }

    @Test
    fun adoptDefaultsReplacesUntouchedInput() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        state.adoptDefaults(CustomerDetailsInput(email = "first@x.co"), visibility)
        assertEquals("first@x.co", state.input.email)
        state.adoptDefaults(CustomerDetailsInput(email = "second@x.co"), visibility)
        assertEquals("second@x.co", state.input.email)
        assertFalse(state.completed)
    }

    @Test
    fun adoptDefaultsKeepsAnEditedDraft() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        state.adoptDefaults(CustomerDetailsInput(email = "first@x.co"), visibility)
        state.onTextChange(PersonalField.EMAIL, "edited@x.co")
        state.adoptDefaults(CustomerDetailsInput(email = "second@x.co"), visibility)
        assertEquals("edited@x.co", state.input.email)
    }

    @Test
    fun adoptDefaultsKeepsAConfirmedDraft() {
        val visibility = CustomerDetailsVisibility.resolve(listOf("email"))
        val state = PersonalDetailsFormState(visibility, ValidationMode.ON_TOUCHED)
        state.onTextChange(PersonalField.EMAIL, "a@b.co")
        assertTrue(state.confirm())
        state.adoptDefaults(CustomerDetailsInput(email = "second@x.co"), visibility)
        assertEquals("a@b.co", state.input.email)
        assertTrue(state.completed)
    }

    @Test
    fun onLeaveAfterRevalidatePromotesAValidDraftOnlyAfterContinue() {
        val emailOnly = CustomerDetailsVisibility.resolve(listOf("email"))
        val withPhone = CustomerDetailsVisibility.resolve(listOf("email", "phone"))

        val neverContinued = PersonalDetailsFormState(emailOnly, ValidationMode.ON_TOUCHED)
        neverContinued.onTextChange(PersonalField.EMAIL, "a@b.co")
        neverContinued.onPhoneCountrySelected("US")
        neverContinued.onTextChange(PersonalField.PHONE, "4155551234")
        neverContinued.revalidate(withPhone)
        neverContinued.onLeave()
        assertFalse(neverContinued.completed)

        val state = PersonalDetailsFormState(emailOnly, ValidationMode.ON_TOUCHED)
        state.onTextChange(PersonalField.EMAIL, "a@b.co")
        assertTrue(state.confirm())
        state.revalidate(withPhone)
        assertFalse(state.completed)
        state.onLeave()
        assertFalse(state.completed)
        state.onPhoneCountrySelected("US")
        state.onTextChange(PersonalField.PHONE, "4155551234")
        state.onLeave()
        assertTrue(state.completed)
        assertEquals("a@b.co", state.input.email)
        assertEquals("4155551234", state.input.phoneNational)
    }
}
