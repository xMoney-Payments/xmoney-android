package com.xmoney.payments.config

import androidx.annotation.RestrictTo

/** When card-field errors are visible and whether they update while typing. Matches React Hook Form `mode`. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object CardValidationDisplay {
    fun shouldShowError(
        mode: ValidationMode,
        fieldBlurred: Boolean,
        submitAttempted: Boolean,
    ): Boolean = when (mode) {
        ValidationMode.ON_CHANGE -> true
        ValidationMode.ON_SUBMIT -> submitAttempted
        ValidationMode.ON_BLUR,
        ValidationMode.ON_TOUCHED,
        -> submitAttempted || fieldBlurred
    }

    fun shouldRefreshDisplayedErrorOnChange(
        mode: ValidationMode,
        fieldBlurred: Boolean,
        submitAttempted: Boolean,
    ): Boolean = when (mode) {
        ValidationMode.ON_CHANGE -> true
        ValidationMode.ON_TOUCHED -> submitAttempted || fieldBlurred
        ValidationMode.ON_BLUR,
        ValidationMode.ON_SUBMIT,
        -> submitAttempted
    }
}
