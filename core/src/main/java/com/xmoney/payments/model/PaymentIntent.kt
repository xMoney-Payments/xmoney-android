package com.xmoney.payments.model

@JvmInline
value class OrderPayload(val value: String)

@JvmInline
value class OrderChecksum(val value: String)

data class OrderCredentials(
    val orderPayload: OrderPayload,
    val orderChecksum: OrderChecksum,
)

data class PaymentIntent(
    val credentials: OrderCredentials,
) {
    val orderPayload: String get() = credentials.orderPayload.value
    val orderChecksum: String get() = credentials.orderChecksum.value

    companion object {
        operator fun invoke(
            orderPayload: OrderPayload,
            orderChecksum: OrderChecksum,
        ): PaymentIntent = PaymentIntent(OrderCredentials(orderPayload, orderChecksum))
    }
}
