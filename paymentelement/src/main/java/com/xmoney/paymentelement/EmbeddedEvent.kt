package com.xmoney.paymentelement

/**
 * [Ready] is emitted by [PaymentElement] once the card form has been laid out
 * and, when Google Pay is offered, the wallet button has pre-drawn. The loading
 * coin is removed before [Ready] is emitted. It is emitted once per mounted surface;
 * a later intent change does not emit it again.
 * [EmbeddedPaymentController.updateOrder] does not emit [Ready] itself.
 */
sealed class EmbeddedEvent {
    data object Ready : EmbeddedEvent()
    data class Processing(val isProcessing: Boolean) : EmbeddedEvent()
}
