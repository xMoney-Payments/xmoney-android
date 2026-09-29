package com.xmoney.googlepay

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.OneShotPreDrawListener
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
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
     * cancels the in-flight one ([CancellationException]).
     */
    suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit = {},
    ) {
        this.onEvent = onEvent
        GooglePay.register()
        if (boundIntent == intent && availability != null && !isOrderConsumed) {
            return
        }
        val generation = ++bindGeneration
        applyUpdatingOrder(true)
        val host = ThreeDSHostController(activity) { liveConfiguration.options.locale }
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
            onResult(
                OrderConsumption.merchantResult(
                    EngineResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD),
                ),
            )
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
 * [GooglePayEvent.Ready] is emitted after the button has pre-drawn, or as soon as
 * Google Pay is known to be unavailable.
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
    LaunchedEffect(controller, intent) {
        controller.updateOrder(intent) { event ->
            if (event !is GooglePayEvent.Ready) currentOnEvent(event)
        }
        surfaceEpoch++
    }

    val methods = controller.availability?.allowedPaymentMethodsJson
    var buttonLaidOut by remember { mutableStateOf(false) }
    if (!methods.isNullOrBlank()) {
        GooglePayButtonWidget(
            appearance = controller.appearance,
            allowedPaymentMethods = methods,
            enabled = controller.isInteractionEnabled && controller.availability?.ready != false,
            onClick = { controller.startPayment() },
            modifier = modifier.onGloballyPositioned { coords ->
                if (coords.size.width > 1 && coords.size.height > 1) buttonLaidOut = true
            },
        )
        ReportButtonDraw(surfaceEpoch, buttonLaidOut) { currentOnEvent(GooglePayEvent.Ready) }
    } else if (surfaceEpoch > 0) {
        LaunchedEffect(surfaceEpoch) { currentOnEvent(GooglePayEvent.Ready) }
    }
}

@Composable
private fun ReportButtonDraw(epoch: Int, laidOut: Boolean, onDrawn: () -> Unit) {
    val view = LocalView.current
    val laidOutState = rememberUpdatedState(laidOut)
    val onDrawnState = rememberUpdatedState(onDrawn)
    LaunchedEffect(epoch) {
        if (epoch <= 0) return@LaunchedEffect
        var frames = 0
        while (frames < MaxButtonFrames && !laidOutState.value) {
            withFrameNanos { }
            frames++
        }
        suspendCancellableCoroutine { continuation ->
            val listener = OneShotPreDrawListener.add(view) {
                if (continuation.isActive) continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { listener.removeListener() }
        }
        onDrawnState.value()
    }
}

private const val MaxButtonFrames = 30
