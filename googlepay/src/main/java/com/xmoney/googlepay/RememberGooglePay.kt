package com.xmoney.googlepay

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val session = PaymentSession(
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
    /** False during [updateOrder], an in-flight charge, or after the order is consumed. */
    var isInteractionEnabled: Boolean by mutableStateOf(true)
        private set

    internal var appearance: WalletAppearance by mutableStateOf(
        liveConfiguration.paymentMethods.googlePay.appearance,
    )
        private set

    private var isUpdatingOrder by mutableStateOf(false)
    private var bindGeneration = 0
    private var createOrderTask: Deferred<PaymentIntent>? = null
    private var boundIntent: PaymentIntent? = null

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
     * This does not emit [GooglePayEvent.Processing]. A newer [updateOrder]
     * cancels the in-flight one ([CancellationException]). The button is not
     * locked while you create [intent] on your server; pass that call to the
     * `createOrder` overload to lock it too. Rebinding the already-bound intent
     * is always a no-op, including while a payment is in progress.
     */
    suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: ((GooglePayEvent) -> Unit)? = null,
    ) {
        onEvent?.let { this.onEvent = it }
        GooglePay.register()
        if (isBound(intent)) return
        if (isProcessing) throw PaymentError.Payment("Payment in progress")
        val generation = ++bindGeneration
        createOrderTask?.cancel()
        createOrderTask = null
        applyUpdatingOrder(true)
        bindLocked(generation, intent)
    }

    /**
     * Lock the wallet button, create the next order with [createOrder], then bind it.
     *
     * Use this when the shopper changes the cart. [startPayment] is a no-op from
     * this call until the new order is bound, including while [createOrder] talks
     * to your server, so a fast tap cannot charge the previous order. If
     * [createOrder] throws, the previous order stays bound and payable, and
     * `onResult` is not called. A newer [updateOrder] cancels this one.
     */
    suspend fun updateOrder(
        onEvent: ((GooglePayEvent) -> Unit)? = null,
        createOrder: suspend () -> PaymentIntent,
    ) = coroutineScope {
        if (isProcessing) throw PaymentError.Payment("Payment in progress")
        onEvent?.let { this@GooglePayController.onEvent = it }
        GooglePay.register()
        val generation = ++bindGeneration
        applyUpdatingOrder(true)
        createOrderTask?.cancel()
        val task = async { createOrder() }
        createOrderTask = task
        val intent = try {
            task.await()
        } catch (e: Throwable) {
            if (generation == bindGeneration) applyUpdatingOrder(false)
            throw e
        } finally {
            if (createOrderTask === task) createOrderTask = null
        }
        if (generation != bindGeneration) throw CancellationException()
        if (isBound(intent)) {
            applyUpdatingOrder(false)
            return@coroutineScope
        }
        bindLocked(generation, intent)
    }

    private fun isBound(intent: PaymentIntent): Boolean =
        boundIntent == intent && availability != null && !isOrderConsumed

    private suspend fun bindLocked(generation: Int, intent: PaymentIntent) {
        val initial = availability == null
        val host = ThreeDSHostController(activity) { liveConfiguration.options.locale }
        bindError = null
        try {
            val loaded = session.bind(intent, activity)
            if (generation != bindGeneration) throw CancellationException()
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
                applyUpdatingOrder(false)
            }
            throw e
        } catch (e: Exception) {
            if (generation != bindGeneration) throw CancellationException()
            applyUpdatingOrder(false)
            if (initial) {
                bindError = if (e is PaymentError) e.merchantMessage() else PaymentError.GENERIC_LOAD
                onResult(
                    OrderConsumption.merchantResult(
                        EngineResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD),
                    ),
                )
            }
            throw e
        }
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
        isInteractionEnabled = !isUpdatingOrder && session.isInteractionEnabled
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
    val controller = remember(configuration.publicKey, configuration.options.locale, activity) {
        GooglePayController(
            configuration = configuration,
            activity = activity,
            onResult = { currentOnResult.value(it) },
        )
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
 * Standalone Google Pay button. When [intent] changes, calls
 * [GooglePayController.updateOrder]. The button stays disabled until that returns.
 * [GooglePayEvent.Ready] is emitted once per mounted surface, after the button has
 * first pre-drawn, or as soon as Google Pay is known to be unavailable. A later
 * [intent] change does not emit it again.
 */
@Composable
fun GooglePayButton(
    controller: GooglePayController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (GooglePayEvent) -> Unit = {},
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    var surfaceEpoch by remember { mutableIntStateOf(0) }
    var readyEmitted by remember { mutableStateOf(false) }
    val emitReady = {
        if (!readyEmitted) {
            readyEmitted = true
            currentOnEvent(GooglePayEvent.Ready)
        }
    }
    val isProcessing = controller.isProcessing
    LaunchedEffect(controller, intent, isProcessing) {
        try {
            controller.updateOrder(intent) { event ->
                if (event !is GooglePayEvent.Ready) currentOnEvent(event)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaymentError.Payment) {
            return@LaunchedEffect
        } catch (_: Exception) {
            if (controller.bindError != null && controller.availability == null) {
                surfaceEpoch++
            }
            return@LaunchedEffect
        }
        surfaceEpoch++
    }

    val methods = controller.availability?.allowedPaymentMethodsJson
    var buttonDrawn by remember { mutableStateOf(false) }
    if (!methods.isNullOrBlank()) {
        GooglePayButtonWidget(
            appearance = controller.appearance,
            allowedPaymentMethods = methods,
            enabled = controller.isInteractionEnabled && controller.availability?.ready != false,
            onClick = { controller.startPayment() },
            modifier = modifier,
            onDrawn = { buttonDrawn = true },
        )
        ReportButtonDraw(surfaceEpoch, buttonDrawn, emitReady)
    } else if (controller.bindError != null) {
        Text(controller.bindError ?: PaymentError.GENERIC_LOAD, modifier = modifier)
        LaunchedEffect(surfaceEpoch) { emitReady() }
    } else if (surfaceEpoch > 0) {
        LaunchedEffect(surfaceEpoch) { emitReady() }
    }
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
