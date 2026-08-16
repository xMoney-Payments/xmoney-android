package com.xmoney.paymentelement

import com.xmoney.payments.config.PaymentConfig

/**
 * Merchant-hosted Payment Element entry.
 * Prefer [rememberEmbeddedPayment] + [PaymentElement] in Compose.
 */
class EmbeddedPayment(
    val configuration: PaymentConfig,
)
