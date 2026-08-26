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
