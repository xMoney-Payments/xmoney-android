package com.xmoney.paymentelement.ui

import com.xmoney.payments.config.ValidationMode
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
}
