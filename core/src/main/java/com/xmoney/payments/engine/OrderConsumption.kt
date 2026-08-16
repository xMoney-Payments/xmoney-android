package com.xmoney.payments.engine

import androidx.annotation.RestrictTo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.Transaction

/** Shared one-shot order policy for Sheet, Embedded, and standalone Google Pay. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object OrderConsumption {
    /**
     * Consume on complete, failed, or cancel after the user authorized (e.g. 3DS abandon).
     * Pre-authorize Google Pay dismiss does not consume.
     */
    fun shouldConsume(status: PaymentResult.Status, didAuthorize: Boolean): Boolean =
        when (status) {
            PaymentResult.Status.COMPLETE,
            PaymentResult.Status.FAILED,
            -> true
            PaymentResult.Status.CANCELED -> didAuthorize
        }

    /** Maps an engine result to a merchant-facing outcome, always sanitizing failures. */
    fun merchantResult(result: PaymentResult): Result<Transaction> =
        when (result.status) {
            PaymentResult.Status.COMPLETE -> {
                val transaction = result.transaction
                if (transaction == null) {
                    Result.failure(PaymentError.Payment("Missing transaction"))
                } else {
                    Result.success(transaction)
                }
            }
            PaymentResult.Status.CANCELED -> Result.failure(PaymentError.Canceled())
            PaymentResult.Status.FAILED -> {
                val error = PaymentError.from(
                    result.errorCode ?: "PAYMENT_ERROR",
                    result.errorMessage ?: PaymentError.GENERIC_PAYMENT,
                )
                Result.failure(PaymentError.from(error.code, error.merchantMessage()))
            }
        }
}
