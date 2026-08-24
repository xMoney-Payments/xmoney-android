package com.xmoney.payments.engine

import androidx.annotation.RestrictTo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.Transaction

/** Internal engine status bag. Merchants see [com.xmoney.payments.model.PaymentResult]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class EngineResult(
    val status: Status,
    val transaction: Transaction?,
    val errorCode: String?,
    val errorMessage: String?,
) {
    enum class Status(val raw: String) {
        COMPLETE("complete"),
        FAILED("failed"),
        CANCELED("canceled"),
    }

    companion object {
        fun failed(error: PaymentError): EngineResult = EngineResult(
            status = Status.FAILED,
            transaction = null,
            errorCode = error.code,
            errorMessage = error.merchantMessage(),
        )

        fun failed(code: String, message: String): EngineResult = EngineResult(
            status = Status.FAILED,
            transaction = null,
            errorCode = code,
            errorMessage = PaymentError.from(code, message).merchantMessage(),
        )

        fun canceled(): EngineResult = EngineResult(
            status = Status.CANCELED,
            transaction = null,
            errorCode = null,
            errorMessage = null,
        )
    }
}
