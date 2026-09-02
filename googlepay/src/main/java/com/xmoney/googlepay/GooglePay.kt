package com.xmoney.googlepay

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.googlepay.internal.GooglePayHostActivity
import com.xmoney.googlepay.internal.GooglePaySessionRegistry
import com.xmoney.googlepay.internal.GooglePayBootstrap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Site/config allows Google Pay ([isAvailable]) and Play Wallet reports a
 * usable method ([isReady]). Same flags as [GooglePayController].
 */
data class GooglePayAvailability(
    val isAvailable: Boolean,
    val isReady: Boolean,
)

/**
 * Public entry point for standalone Google Pay.
 *
 * Instance-based so merchants can hold multiple configurations. A second
 * [present] is a no-op while this instance has an open host; call [updateOrder]
 * to rebind the order, or [dismiss] then [present] for a new overlay.
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

    /**
     * Bind without UI and return controller-equivalent availability flags.
     * Failures (missing Play Wallet, load error) resolve to both flags false.
     */
    suspend fun availability(
        activity: FragmentActivity,
        intent: PaymentIntent,
    ): GooglePayAvailability {
        GooglePay.register()
        val config = configuration.copy(
            paymentMethods = configuration.paymentMethods.copy(
                googlePay = configuration.paymentMethods.googlePay.copy(enabled = true),
            ),
        )
        return try {
            val session = PaymentSession(config, intent, activity.applicationContext)
            val loaded = session.bind(intent, activity)
            GooglePayAvailability(
                isAvailable = loaded.googlePayAvailable,
                isReady = loaded.googlePayReady,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GooglePayAvailability(isAvailable = false, isReady = false)
        }
    }

    /**
     * Present the Google Pay host for [intent].
     *
     * A second [present] while this instance already has an open host is a
     * no-op. Use [updateOrder] to rebind a new [PaymentIntent], or [dismiss]
     * then [present] for a new overlay.
     */
    fun present(
        activity: FragmentActivity,
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit = {},
        onResult: (PaymentResult) -> Unit,
    ) {
        GooglePay.register()
        if (GooglePaySessionRegistry.isActive(lastRequestId)) return
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

    /**
     * Rebind a new signed order on the open Google Pay host without dismissing it.
     *
     * Call after [present], while the overlay is still open. The wallet button
     * is disabled until this returns. This does not emit [GooglePayEvent.Processing].
     * While a charge is in flight the call returns without [GooglePayEvent.Ready].
     * A newer [updateOrder] cancels the in-flight one ([CancellationException]).
     *
     * @throws IllegalStateException if this instance has no open host
     */
    suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit = {},
    ) {
        GooglePay.register()
        val requestId = lastRequestId
            ?: throw IllegalStateException("Google Pay is not presented")
        withContext(Dispatchers.Main.immediate) {
            GooglePaySessionRegistry.updateOrder(requestId, intent, onEvent)
        }
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
