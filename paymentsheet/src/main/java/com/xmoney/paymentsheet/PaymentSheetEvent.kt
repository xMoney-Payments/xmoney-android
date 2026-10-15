package com.xmoney.paymentsheet

/**
 * [Ready] is emitted once the sheet content has been laid out and, when Google
 * Pay is offered, the wallet button has pre-drawn. The sheet keeps its loading
 * coin up until then.
 */
sealed class PaymentSheetEvent {
    data object Ready : PaymentSheetEvent()
    data class Processing(val isProcessing: Boolean) : PaymentSheetEvent()
}
