package com.xmoney.paymentsheet

sealed class PaymentSheetEvent {
    data object Ready : PaymentSheetEvent()
    data class Processing(val isProcessing: Boolean) : PaymentSheetEvent()
}
