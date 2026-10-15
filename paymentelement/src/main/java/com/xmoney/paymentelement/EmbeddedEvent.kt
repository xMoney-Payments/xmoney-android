package com.xmoney.paymentelement

import com.xmoney.payments.model.PaymentError

/**
 * [Ready] is emitted by [PaymentElement] once the card form has been laid out
 * and, when Google Pay is offered, the wallet button has pre-drawn. The loading
 * coin is removed before [Ready] is emitted. It is emitted once per mounted surface;
 * a later intent change does not emit it again.
 * [EmbeddedPaymentController.updateOrder] does not emit [Ready] itself. [Ready]
 * is never emitted on the failure state; a retry that shows the form emits it then.
 *
 * [UpdateFailed] is emitted when binding a new order fails after an order was
 * already bound. Pay stays locked on the stale order until a bind succeeds.
 */
sealed class EmbeddedEvent {
    data object Ready : EmbeddedEvent()
    data class Processing(val isProcessing: Boolean) : EmbeddedEvent()
    data class UpdateFailed(val error: PaymentError) : EmbeddedEvent()
}
