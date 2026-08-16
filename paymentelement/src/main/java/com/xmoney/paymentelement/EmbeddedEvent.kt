package com.xmoney.paymentelement

sealed class EmbeddedEvent {
    data object Ready : EmbeddedEvent()
    data class Processing(val isProcessing: Boolean) : EmbeddedEvent()
}
