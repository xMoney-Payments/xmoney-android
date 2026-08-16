package com.xmoney.googlepay

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.googlepay.internal.GooglePayHostActivity
import com.xmoney.googlepay.internal.GooglePaySessionRegistry
import com.xmoney.googlepay.internal.GooglePayBootstrap
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Public entry point for standalone Google Pay.
 *
 * Instance-based so merchants can hold multiple configurations. Concurrent
 * [present] calls use request-scoped sessions and do not overwrite each other.
 */
class GooglePay(
    private val configuration: PaymentConfig,
) {
    private var lastActivity: WeakReference<FragmentActivity>? = null
    private var lastRequestId: String? = null

    companion object {
        /** Installs Play Wallet hooks on core. Idempotent; also runs from a ContentProvider on link. */
        @JvmStatic
        fun register() {
            GooglePayBootstrap.install()
        }
    }

    fun present(
        activity: FragmentActivity,
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit = {},
        onResult: (PaymentResult) -> Unit,
    ) {
        GooglePay.register()
        lastActivity = WeakReference(activity)
        val config = configuration.copy(
            paymentMethods = configuration.paymentMethods.copy(
                googlePay = configuration.paymentMethods.googlePay.copy(enabled = true),
            ),
        ).resolve(intent)
        val requestId = UUID.randomUUID().toString()
        lastRequestId = requestId

        GooglePaySessionRegistry.register(
            requestId,
            GooglePaySessionRegistry.Session(
                onEvent = onEvent,
                onResult = onResult,
                config = config,
            ),
        )

        activity.startActivity(GooglePayHostActivity.createIntent(activity, requestId, config))
    }

    /** Dismiss an in-flight Google Pay host started by this instance, if still open. */
    fun dismiss() {
        GooglePaySessionRegistry.finishHost(lastRequestId)
    }
}

sealed class GooglePayEvent {
    data object Ready : GooglePayEvent()
    data class Processing(val isProcessing: Boolean) : GooglePayEvent()
}
