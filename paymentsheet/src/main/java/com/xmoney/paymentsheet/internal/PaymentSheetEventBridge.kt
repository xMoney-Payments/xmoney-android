package com.xmoney.paymentsheet.internal

import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry
import com.xmoney.paymentsheet.PaymentSheetEvent

internal object PaymentSheetEventBridge {
    fun emit(requestId: String, event: PaymentSheetEvent) {
        CheckoutSessionRegistry.get(requestId)?.onEvent?.invoke(event)
    }
}
