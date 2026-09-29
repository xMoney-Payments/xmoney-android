package com.xmoney.paymentelement

/**
 * [Ready] is emitted by [PaymentElement] once the card form has been laid out
 * and, when Google Pay is offered, the wallet button has pre-drawn. A later
 * intent change emits it again after that layout. The loading coin stays up
 * only until the first [Ready]. [EmbeddedPaymentController.updateOrder] does
 * not emit [Ready] itself.
 */
sealed class EmbeddedEvent {
    data object Ready : EmbeddedEvent()
    data class Processing(val isProcessing: Boolean) : EmbeddedEvent()
}
