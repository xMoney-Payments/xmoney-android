package com.xmoney.payments.engine

import androidx.annotation.RestrictTo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult

/** Shared one-shot order policy for Sheet, Embedded, and standalone Google Pay. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object OrderConsumption {
    /**
     * Consume on complete, failed, or cancel after the user authorized (e.g. 3DS abandon).
     * Pre-authorize wallet dismiss still delivers [PaymentResult.Canceled] and does not consume.
     */
    fun shouldConsume(status: EngineResult.Status, didAuthorize: Boolean): Boolean =
        when (status) {
            EngineResult.Status.COMPLETE,
            EngineResult.Status.FAILED,
            -> true
            EngineResult.Status.CANCELED -> didAuthorize
        }

    /** Maps an engine result to the merchant-facing [PaymentResult], always sanitizing failures. */
    fun merchantResult(result: EngineResult): PaymentResult =
        when (result.status) {
            EngineResult.Status.COMPLETE -> {
                val transaction = result.transaction
                if (transaction == null) {
                    val error = PaymentError.Payment("Missing transaction")
                    PaymentResult.Failed(PaymentError.from(error.code, error.merchantMessage()))
                } else {
                    PaymentResult.Complete(transaction)
                }
            }
            EngineResult.Status.CANCELED -> PaymentResult.Canceled
            EngineResult.Status.FAILED -> {
                if (result.errorCode == "CANCELED") {
                    PaymentResult.Canceled
                } else {
                    val error = PaymentError.from(
                        result.errorCode ?: "PAYMENT_ERROR",
                        result.errorMessage ?: PaymentError.GENERIC_PAYMENT,
                    )
                    PaymentResult.Failed(PaymentError.from(error.code, error.merchantMessage()))
                }
            }
        }
}
