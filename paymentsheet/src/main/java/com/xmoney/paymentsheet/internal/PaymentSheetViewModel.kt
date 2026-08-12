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
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.PaymentResult

class PaymentSheetViewModel(
    private val savedStateHandle: SavedStateHandle,
    val engine: PaymentEngine,
    val config: ResolvedPaymentConfig,
    val requestId: String,
) : ViewModel() {
    var onEvent: (PaymentSheetEvent) -> Unit = {}
    var onComplete: (PaymentResult) -> Unit = {}

    private var completed = false
    private var cachedTheme: CheckoutTheme? = null
    private var lastThemeDark: Boolean? = null

    fun theme(isDark: Boolean): CheckoutTheme =
        cachedTheme.takeIf { lastThemeDark == isDark }
            ?: CheckoutTheme.resolve(config, isDark).also {
                cachedTheme = it
                lastThemeDark = isDark
            }

    fun finish(result: PaymentResult) {
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
            val engine = PaymentEngine(config, context).also {
                it.onCardHolderVerification =
                    CheckoutSessionRegistry.get(requestId)?.onCardHolderVerification
            }
            return PaymentSheetViewModel(handle, engine, config, requestId) as T
        }
    }
}
