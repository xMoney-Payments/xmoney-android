package com.xmoney.paymentsheet.internal

import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentelement.theme.CheckoutTheme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.PendingPayment
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

    init {
        session.onPendingChanged = { savedStateHandle[KEY_PENDING] = it }
    }

    /** A payment left without a result by a destroyed screen or a killed process. */
    val pendingToRecover: PendingPayment?
        get() = session.pendingPayment ?: savedStateHandle[KEY_PENDING]

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

    /** Survives fragment recreation, so a rotation does not bring the coin or Ready back. */
    var surfaceReady by mutableStateOf(false)
        private set

    fun markSurfaceReady() {
        if (surfaceReady) return
        surfaceReady = true
        onEvent(PaymentSheetEvent.Ready)
    }

    companion object {
        internal const val KEY_PENDING = "xmoney_pending_payment"
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
