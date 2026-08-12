package com.xmoney.googlepay.internal

import com.xmoney.payments.model.OrderPayloadInfo

data class GooglePayAvailability(
    val available: Boolean,
    val ready: Boolean,
    val allowedPaymentMethodsJson: String?,
    val orderInfo: OrderPayloadInfo? = null,
)
