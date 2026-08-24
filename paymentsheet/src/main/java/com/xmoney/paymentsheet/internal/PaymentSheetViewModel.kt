package com.xmoney.paymentsheet.internal

import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentelement.theme.CheckoutTheme

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.engine.EngineResult

class PaymentSheetViewModel(
    private val savedStateHandle: SavedStateHandle,
    val session: PaymentSession,
    val config: ResolvedPaymentConfig,
    val requestId: String,
) : ViewModel() {
    var onEvent: (PaymentSheetEvent) -> Unit = {}
    var onComplete: (EngineResult) -> Unit = {}

    private var completed = false
    private var cachedTheme: CheckoutTheme? = null
    private var lastThemeDark: Boolean? = null

    val intent: PaymentIntent = PaymentIntent(
        OrderPayload(config.orderPayload),
        OrderChecksum(config.orderChecksum),
    )

    fun theme(isDark: Boolean): CheckoutTheme =
        cachedTheme.takeIf { lastThemeDark == isDark }
            ?: CheckoutTheme.resolve(config, isDark).also {
                cachedTheme = it
                lastThemeDark = isDark
            }

    fun finish(result: EngineResult) {
        if (completed) return
        completed = true
        onComplete(result)
    }

    class Factory(
        private val context: Context,
        private val config: ResolvedPaymentConfig,
        private val requestId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val handle = extras.createSavedStateHandle()
            val paymentConfig = PaymentConfig(
                publicKey = config.publicKey,
                card = config.card,
                paymentMethods = config.paymentMethods,
                options = config.options,
            )
            val intent = PaymentIntent(
                OrderPayload(config.orderPayload),
                OrderChecksum(config.orderChecksum),
            )
            val session = PaymentSession(paymentConfig, intent, context)
            CheckoutSessionRegistry.get(requestId)?.onCardHolderVerification?.let {
                session.onCardHolderVerification = it
            }
            return PaymentSheetViewModel(handle, session, config, requestId) as T
        }
    }
}
