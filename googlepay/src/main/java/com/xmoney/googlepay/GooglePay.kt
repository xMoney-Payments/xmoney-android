package com.xmoney.googlepay

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.googlepay.internal.GooglePayHostActivity
import com.xmoney.googlepay.internal.GooglePaySessionRegistry
import com.xmoney.googlepay.internal.GooglePayBootstrap
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.UpdateOrderSuperseded
import com.xmoney.payments.util.onMainThread
import kotlinx.coroutines.CancellationException
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
     * is disabled until this returns. This does not emit [GooglePayEvent.Processing]
     * or [GooglePayEvent.Ready]. The latest call wins: an in-flight update throws
     * [UpdateOrderSuperseded], even when [intent] is the order already bound.
     * While a charge is in flight the bound order is a no-op and a different one
     * throws [PaymentError.PaymentInProgress]. If the bind fails after an order was
     * bound, the wallet stays locked on that stale order, [GooglePayEvent.UpdateFailed]
     * is emitted, and the error is thrown. Runs on the main thread.
     *
     * @throws IllegalStateException if this instance has no open host
     */
    suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: ((GooglePayEvent) -> Unit)? = null,
    ) {
        GooglePay.register()
        val requestId = lastRequestId
            ?: throw IllegalStateException("Google Pay is not presented")
        onMainThread {
            GooglePaySessionRegistry.updateOrder(requestId, intent, onEvent)
        }
    }

    /**
     * Create and bind a new signed order on the open Google Pay host, and return it.
     *
     * The wallet is disabled before [createOrder] starts. If [createOrder] throws,
     * the previous order stays payable and the error is thrown, without
     * [GooglePayEvent.UpdateFailed]. A newer update supersedes this one
     * ([UpdateOrderSuperseded]) and cancels [createOrder]. [createOrder] runs on the
     * main thread, so it must be a non-blocking suspend call.
     */
    suspend fun updateOrder(
        onEvent: ((GooglePayEvent) -> Unit)? = null,
        createOrder: suspend () -> PaymentIntent,
    ): PaymentIntent {
        GooglePay.register()
        val requestId = lastRequestId
            ?: throw IllegalStateException("Google Pay is not presented")
        return onMainThread {
            GooglePaySessionRegistry.updateOrder(requestId, onEvent, createOrder)
        }
    }

    /** Dismiss an in-flight Google Pay host started by this instance, if still open. */
    fun dismiss() {
        GooglePaySessionRegistry.finishHost(lastRequestId)
    }
}

/**
 * [Ready] from [GooglePayButton] is emitted once per mounted surface, after the
 * button has pre-drawn, or as soon as Google Pay is known to be unavailable for a
 * loaded order. It is never emitted on the failure state.
 * [GooglePay.present] emits [Ready] after its first successful bind; an initial
 * failure delivers `Failed` without [Ready]. Order updates do not emit it.
 *
 * [UpdateFailed] is emitted when binding a new order fails after an order was
 * already bound. The wallet stays locked on the stale order until a bind succeeds.
 */
sealed class GooglePayEvent {
    data object Ready : GooglePayEvent()
    data class Processing(val isProcessing: Boolean) : GooglePayEvent()
    data class UpdateFailed(val error: PaymentError) : GooglePayEvent()
}
