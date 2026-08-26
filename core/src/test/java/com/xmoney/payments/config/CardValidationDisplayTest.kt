package com.xmoney.payments.config

import org.junit.Assert.assertEquals
import org.junit.Test

class CardValidationDisplayTest {
    private data class Case(
        val mode: ValidationMode,
        val fieldBlurred: Boolean,
        val submitAttempted: Boolean,
        val show: Boolean,
        val refreshOnChange: Boolean,
    )

    @Test
    fun displayAndRefreshTruthTable() {
        val cases = listOf(
            // onChange: always show, always live
            Case(ValidationMode.ON_CHANGE, false, false, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_CHANGE, true, false, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_CHANGE, false, true, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_CHANGE, true, true, show = true, refreshOnChange = true),

            // onSubmit: hidden until Pay, then live
            Case(ValidationMode.ON_SUBMIT, false, false, show = false, refreshOnChange = false),
            Case(ValidationMode.ON_SUBMIT, true, false, show = false, refreshOnChange = false),
            Case(ValidationMode.ON_SUBMIT, false, true, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_SUBMIT, true, true, show = true, refreshOnChange = true),

            // onBlur: show after this field blurs; frozen until next blur (or Pay)
            Case(ValidationMode.ON_BLUR, false, false, show = false, refreshOnChange = false),
            Case(ValidationMode.ON_BLUR, true, false, show = true, refreshOnChange = false),
            Case(ValidationMode.ON_BLUR, false, true, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_BLUR, true, true, show = true, refreshOnChange = true),

            // onTouched: show after this field blurs, then live
            Case(ValidationMode.ON_TOUCHED, false, false, show = false, refreshOnChange = false),
            Case(ValidationMode.ON_TOUCHED, true, false, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_TOUCHED, false, true, show = true, refreshOnChange = true),
            Case(ValidationMode.ON_TOUCHED, true, true, show = true, refreshOnChange = true),
        )

        for (item in cases) {
            assertEquals(
                "shouldShowError ${item.mode} blurred=${item.fieldBlurred} submit=${item.submitAttempted}",
                item.show,
                CardValidationDisplay.shouldShowError(item.mode, item.fieldBlurred, item.submitAttempted),
            )
            assertEquals(
                "shouldRefreshDisplayedErrorOnChange ${item.mode} blurred=${item.fieldBlurred} submit=${item.submitAttempted}",
                item.refreshOnChange,
                CardValidationDisplay.shouldRefreshDisplayedErrorOnChange(
                    item.mode,
                    item.fieldBlurred,
                    item.submitAttempted,
                ),
            )
        }
    }

    @Test
    fun unknownAndNullModeDefaultToOnTouched() {
        assertEquals(ValidationMode.ON_TOUCHED, ValidationMode.from(null))
        assertEquals(ValidationMode.ON_TOUCHED, ValidationMode.from("nope"))
        assertEquals(ValidationMode.ON_CHANGE, ValidationMode.from("onChange"))
        assertEquals(ValidationMode.ON_TOUCHED, CardConfig().validationMode)
    }
}
