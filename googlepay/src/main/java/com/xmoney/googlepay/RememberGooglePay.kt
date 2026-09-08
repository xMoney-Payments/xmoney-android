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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

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
            onEvent(GooglePayEvent.Ready)
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
            this.onEvent(GooglePayEvent.Ready)
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
 */
@Composable
fun GooglePayButton(
    controller: GooglePayController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (GooglePayEvent) -> Unit = {},
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(controller, intent) {
        controller.updateOrder(intent) { currentOnEvent(it) }
    }

    val methods = controller.availability?.allowedPaymentMethodsJson
    if (!methods.isNullOrBlank()) {
        GooglePayButtonWidget(
            appearance = controller.appearance,
            allowedPaymentMethods = methods,
            enabled = controller.isInteractionEnabled && controller.availability?.ready != false,
            onClick = { controller.startPayment() },
            modifier = modifier,
        )
    }
}
