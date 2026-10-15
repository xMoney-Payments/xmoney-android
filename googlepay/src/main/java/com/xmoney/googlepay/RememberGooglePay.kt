package com.xmoney.googlepay

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xmoney.googlepay.internal.GooglePayAvailability
import com.xmoney.googlepay.internal.GooglePayWalletOutcome
import com.xmoney.googlepay.ui.GooglePayButton as GooglePayButtonWidget
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.UpdateOrderSuperseded
import com.xmoney.payments.util.onMainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Compose-facing controller for embedding Google Pay in a merchant screen.
 *
 * After COMPLETE, FAILED, or post-submit CANCELED the bound order checksum is
 * consumed — the button stays mounted but disabled until [updateOrder] with a new order.
 */
class GooglePayController internal constructor(
    configuration: PaymentConfig,
    private val activity: FragmentActivity,
    private val onResult: (PaymentResult) -> Unit,
) {
    private var liveConfiguration = configuration
    @VisibleForTesting
    internal var session = PaymentSession(
        liveConfiguration.copy(
            paymentMethods = liveConfiguration.paymentMethods.copy(
                googlePay = liveConfiguration.paymentMethods.googlePay.copy(enabled = true),
            ),
        ),
        placeholderIntent(),
        activity.applicationContext,
    )

    internal var availability by mutableStateOf<GooglePayAvailability?>(null)
        private set
    internal var isProcessing by mutableStateOf(false)
        private set
    internal var bindError: String? by mutableStateOf(null)
        private set
    var isOrderConsumed: Boolean by mutableStateOf(false)
        private set
    /**
     * False during [updateOrder], an in-flight charge, after the order is consumed,
     * or while the bound order is stale because a later bind failed.
     */
    var isInteractionEnabled: Boolean by mutableStateOf(true)
        private set
    internal var isStaleOrder by mutableStateOf(false)
        private set

    internal var appearance: WalletAppearance by mutableStateOf(
        liveConfiguration.paymentMethods.googlePay.appearance,
    )
        private set

    private var isUpdatingOrder by mutableStateOf(false)
    private var bindGeneration = 0
    private var createOrderTask: Deferred<PaymentIntent>? = null
    internal var boundIntent: PaymentIntent? = null
        private set
    private var inFlightOrder: PaymentIntent? = null
    private var initialFailureReported = false

    /** Site/config allows Google Pay for this order. */
    val isAvailable: Boolean
        get() = availability?.available == true

    /** Play Wallet reports a usable payment method on this device. */
    val isReady: Boolean
        get() = availability?.ready == true

    private var authorizer: DigitalWalletAuthorizing? = null
    private var threeDS: ThreeDSHostController? = null
    private var onEvent: (GooglePayEvent) -> Unit = {}
    private var pendingResolution: ActivityResult? = null
    private var pendingLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    fun bindWalletResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        authorizer?.bindResolutionLauncher(launcher)
        pendingLauncher = launcher
    }

    fun handleWalletResolution(result: ActivityResult) {
        val wallet = authorizer
        if (wallet == null) {
            pendingResolution = result
            return
        }
        wallet.handleResolutionResult(result)
        if (wallet.hasPendingWalletAuthorization) {
            startPayment()
        }
    }

    /**
     * Rebind a new signed order without recreating the wallet button.
     *
     * The wallet button is disabled until this returns ([isInteractionEnabled]).
     * This does not emit [GooglePayEvent.Processing] or [GooglePayEvent.Ready].
     * The latest call wins: an in-flight [updateOrder] throws [UpdateOrderSuperseded],
     * even when [intent] is the order already bound. The button is not locked while
     * you create [intent] on your server; pass that call to the `createOrder`
     * overload to lock it too. While a payment is in progress the bound intent is a
     * no-op and a different one throws [PaymentError.PaymentInProgress]. If the bind
     * fails after an order was bound, the button stays locked on that stale order,
     * [GooglePayEvent.UpdateFailed] is emitted, the error is thrown, and `onResult`
     * is not called. Runs on the main thread.
     */
    suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: ((GooglePayEvent) -> Unit)? = null,
    ): Unit = onMainThread {
        onEvent?.let { this@GooglePayController.onEvent = it }
        GooglePay.register()
        if (isProcessing) {
            if (isBound(intent)) return@onMainThread
            throw PaymentError.PaymentInProgress()
        }
        val generation = supersede()
        if (isBound(intent)) {
            isStaleOrder = false
            applyUpdatingOrder(false)
            return@onMainThread
        }
        applyUpdatingOrder(true)
        bindLocked(generation, intent)
    }

    /**
     * Lock the wallet button, create the next order with [createOrder], bind it,
     * and return it.
     *
     * Use this when the shopper changes the cart. [startPayment] is a no-op from
     * this call until the new order is bound, including while [createOrder] talks
     * to your server, so a fast tap cannot charge the previous order. If
     * [createOrder] throws, the previous order stays bound and payable, the error
     * is thrown, and no [GooglePayEvent.UpdateFailed] is emitted. A newer
     * [updateOrder] supersedes this one ([UpdateOrderSuperseded]) and cancels
     * [createOrder]. [createOrder] runs on the main thread, so it must be a
     * non-blocking suspend call.
     */
    suspend fun updateOrder(
        onEvent: ((GooglePayEvent) -> Unit)? = null,
        createOrder: suspend () -> PaymentIntent,
    ): PaymentIntent = onMainThread {
        onEvent?.let { this@GooglePayController.onEvent = it }
        if (isProcessing) throw PaymentError.PaymentInProgress()
        GooglePay.register()
        val generation = supersede()
        applyUpdatingOrder(true)
        val task = async { createOrder() }
        createOrderTask = task
        val intent = try {
            task.await()
        } catch (e: Throwable) {
            if (generation != bindGeneration) throw UpdateOrderSuperseded()
            applyUpdatingOrder(false)
            throw e
        } finally {
            if (createOrderTask === task) createOrderTask = null
        }
        if (generation != bindGeneration) throw UpdateOrderSuperseded()
        if (isBound(intent)) {
            isStaleOrder = false
            applyUpdatingOrder(false)
            return@onMainThread intent
        }
        bindLocked(generation, intent)
        intent
    }

    /**
     * Surface-driven rebind from [GooglePayButton]. The bound intent, or the intent
     * an in-flight merchant call is already binding, is a no-op that cancels nothing.
     */
    internal suspend fun rebindFromSurface(
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit,
    ) {
        this.onEvent = onEvent
        if (isBound(intent) || inFlightOrder == intent) return
        updateOrder(intent)
    }

    internal val locale: String
        get() = liveConfiguration.options.locale

    internal fun updateLocale(locale: String) {
        if (liveConfiguration.options.locale == locale) return
        liveConfiguration = liveConfiguration.copy(
            options = liveConfiguration.options.copy(locale = locale),
        )
    }

    private fun supersede(): Int {
        val generation = ++bindGeneration
        createOrderTask?.cancel()
        createOrderTask = null
        inFlightOrder = null
        return generation
    }

    private fun isBound(intent: PaymentIntent): Boolean =
        boundIntent == intent && availability != null && !isOrderConsumed && session.isBound(intent)

    private suspend fun bindLocked(generation: Int, intent: PaymentIntent) {
        val initial = availability == null
        val host = ThreeDSHostController(activity) { liveConfiguration.options.locale }
        inFlightOrder = intent
        try {
            val candidate = session.load(intent, activity)
            if (generation != bindGeneration) throw UpdateOrderSuperseded()
            session.commit(candidate)
            val loaded = candidate.state
            inFlightOrder = null
            bindError = null
            isStaleOrder = false
            initialFailureReported = false
            threeDS = host
            authorizer = session.makeWalletAuthorizer(host, activity)
            pendingLauncher?.let { authorizer?.bindResolutionLauncher(it) }
            boundIntent = intent
            availability = GooglePayAvailability(
                available = loaded.googlePayAvailable,
                ready = loaded.googlePayReady,
                allowedPaymentMethodsJson = loaded.googlePayAllowedPaymentMethods,
                orderInfo = loaded.orderInfo,
            )
            isOrderConsumed = session.isOrderConsumed
            isProcessing = session.isProcessing
            applyUpdatingOrder(false)
            val pending = pendingResolution
            pendingResolution = null
            if (pending != null) {
                authorizer?.handleResolutionResult(pending)
                if (pending.resultCode == Activity.RESULT_OK &&
                    authorizer?.hasPendingWalletAuthorization == true
                ) {
                    startPayment()
                }
            }
        } catch (e: CancellationException) {
            if (generation == bindGeneration) {
                inFlightOrder = null
                applyUpdatingOrder(false)
            }
            throw e
        } catch (e: Exception) {
            if (generation != bindGeneration) throw UpdateOrderSuperseded()
            inFlightOrder = null
            val error = e as? PaymentError ?: PaymentError.Load(e.message ?: PaymentError.GENERIC_LOAD)
            if (initial) {
                bindError = error.merchantMessage()
                if (!initialFailureReported) {
                    initialFailureReported = true
                    onResult(OrderConsumption.merchantResult(EngineResult.failed(error)))
                }
            } else {
                isStaleOrder = true
            }
            applyUpdatingOrder(false)
            if (!initial) onEvent(GooglePayEvent.UpdateFailed(error))
            throw e
        }
    }

    @VisibleForTesting
    internal fun setProcessingForTest(processing: Boolean) {
        isProcessing = processing
        syncInteractionEnabled()
    }

    fun updateAppearance(next: WalletAppearance) {
        appearance = next
        liveConfiguration = liveConfiguration.copy(
            paymentMethods = liveConfiguration.paymentMethods.copy(
                googlePay = liveConfiguration.paymentMethods.googlePay.copy(appearance = next),
            ),
        )
    }

    fun startPayment() {
        if (!isInteractionEnabled) return
        val wallet = authorizer ?: return
        isProcessing = true
        syncInteractionEnabled()
        onEvent(GooglePayEvent.Processing(true))
        activity.lifecycleScope.launch {
            val result = session.startWallet(wallet)
            isProcessing = session.isProcessing
            isOrderConsumed = session.isOrderConsumed
            syncInteractionEnabled()
            GooglePayWalletOutcome.deliver(result, onEvent, onResult)
        }
    }

    private fun applyUpdatingOrder(updating: Boolean) {
        isUpdatingOrder = updating
        syncInteractionEnabled()
    }

    private fun syncInteractionEnabled() {
        isInteractionEnabled = !isUpdatingOrder && !isStaleOrder && !isProcessing &&
            session.isInteractionEnabled
    }

    companion object {
        private fun placeholderIntent(): PaymentIntent =
            PaymentIntent(
                com.xmoney.payments.model.OrderPayload(""),
                com.xmoney.payments.model.OrderChecksum(""),
            )
    }
}

@Composable
fun rememberGooglePay(
    configuration: PaymentConfig,
    onResult: (PaymentResult) -> Unit,
): GooglePayController {
    val activity = LocalContext.current as FragmentActivity
    val currentOnResult = rememberUpdatedState(onResult)
    GooglePay.register()
    val controller = remember(configuration.publicKey, activity) {
        GooglePayController(
            configuration = configuration,
            activity = activity,
            onResult = { currentOnResult.value(it) },
        )
    }
    SideEffect {
        controller.updateLocale(configuration.options.locale)
    }
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        controller.handleWalletResolution(result)
    }
    SideEffect {
        controller.bindWalletResolutionLauncher(resolutionLauncher)
    }
    return controller
}

/**
 * Standalone Google Pay button. When [intent] changes, binds it on [controller].
 * The button stays disabled until the bind finishes. An [intent] received while a
 * payment is in progress is kept and bound once the payment ends.
 * [GooglePayEvent.Ready] is emitted once per mounted surface, after the button has
 * first pre-drawn, or as soon as Google Pay is known to be unavailable for a loaded
 * order. It is never emitted on the failure state; a later successful bind emits it.
 * A later [intent] change does not emit it. A failed later bind emits
 * [GooglePayEvent.UpdateFailed] and keeps the button locked.
 */
@Composable
fun GooglePayButton(
    controller: GooglePayController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (GooglePayEvent) -> Unit = {},
) {
    val currentOnEvent = rememberUpdatedState(onEvent)
    val forward: (GooglePayEvent) -> Unit = remember(controller) {
        { event -> if (event !is GooglePayEvent.Ready) currentOnEvent.value(event) }
    }
    var readyEmitted by remember(controller) { mutableStateOf(false) }
    var buttonDrawn by remember(controller) { mutableStateOf(false) }
    var deferredIntent by remember(controller) { mutableStateOf<PaymentIntent?>(null) }
    val emitReady = {
        if (!readyEmitted) {
            readyEmitted = true
            currentOnEvent.value(GooglePayEvent.Ready)
        }
    }

    suspend fun bind(target: PaymentIntent) {
        try {
            controller.rebindFromSurface(target, forward)
            if (deferredIntent == target) deferredIntent = null
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaymentError.PaymentInProgress) {
            deferredIntent = target
        } catch (_: Exception) {
            // Reported through onResult (initial bind) or GooglePayEvent.UpdateFailed.
        }
    }

    LaunchedEffect(controller, intent) {
        deferredIntent = null
        bind(intent)
    }
    val isProcessing = controller.isProcessing
    LaunchedEffect(controller, isProcessing) {
        val pending = deferredIntent
        if (!isProcessing && pending != null) bind(pending)
    }

    val availability = controller.availability
    val methods = availability?.allowedPaymentMethodsJson
    val surfaceEpoch = if (availability != null) 1 else 0
    if (!methods.isNullOrBlank()) {
        GooglePayButtonWidget(
            appearance = controller.appearance,
            allowedPaymentMethods = methods,
            enabled = controller.isInteractionEnabled && availability.ready != false,
            onClick = { controller.startPayment() },
            modifier = modifier,
            onDrawn = { buttonDrawn = true },
        )
        ReportButtonDraw(surfaceEpoch, buttonDrawn, emitReady)
    } else if (availability == null && controller.bindError != null) {
        GooglePayLoadFailed(controller.locale, modifier)
    } else if (availability != null) {
        LaunchedEffect(controller) { emitReady() }
    }
}

@Composable
private fun GooglePayLoadFailed(locale: String, modifier: Modifier) {
    val isDark = isSystemInDarkTheme()
    Text(
        text = Strings.text("sheet.loadFailed", locale),
        color = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F),
        style = MaterialTheme.typography.body2,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ReportButtonDraw(epoch: Int, drawn: Boolean, onDrawn: () -> Unit) {
    val drawnState = rememberUpdatedState(drawn)
    val onDrawnState = rememberUpdatedState(onDrawn)
    LaunchedEffect(epoch) {
        if (epoch <= 0) return@LaunchedEffect
        val drawnInTime = withTimeoutOrNull(SurfaceDrawTimeoutMillis) {
            while (!drawnState.value) withFrameNanos { }
            true
        } == true
        if (!drawnInTime) {
            android.util.Log.d("XMoneySurfaceDraw", "Google Pay draw timed out; revealing fallback")
        }
        onDrawnState.value()
    }
}

private const val SurfaceDrawTimeoutMillis = 2_000L
