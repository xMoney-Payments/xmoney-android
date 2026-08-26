package com.xmoney.googlepay.internal

import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.model.PaymentResult

/**
 * Merchant-facing Google Pay outcome. Pre-auth cancel still delivers
 * [PaymentResult.Canceled] (order is not consumed).
 */
internal object GooglePayWalletOutcome {
    fun deliver(
        result: EngineResult,
        onEvent: (GooglePayEvent) -> Unit,
        onResult: (PaymentResult) -> Unit,
    ) {
        onEvent(GooglePayEvent.Processing(false))
        onResult(OrderConsumption.merchantResult(result))
    }
}
