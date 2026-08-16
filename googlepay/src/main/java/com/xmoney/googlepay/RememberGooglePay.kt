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
import com.xmoney.googlepay.ui.GooglePayButton as GooglePayButtonWidget
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.DigitalWalletAuthorizing
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
 * consumed — the button stays mounted but disabled until [bind] with a new order.
 */
class GooglePayController internal constructor(
    private val configuration: PaymentConfig,
    private val activity: FragmentActivity,
    private val onResult: (PaymentResult) -> Unit,
) {
    private val session = PaymentSession(
        configuration.copy(
            paymentMethods = configuration.paymentMethods.copy(
                googlePay = configuration.paymentMethods.googlePay.copy(enabled = true),
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

    internal val appearance: WalletAppearance
        get() = configuration.paymentMethods.googlePay.appearance

    val isInteractionEnabled: Boolean
        get() = session.isInteractionEnabled

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

    suspend fun bind(
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit = {},
    ) {
        this.onEvent = onEvent
        GooglePay.register()
        val host = ThreeDSHostController(activity) { configuration.options.locale }
        try {
            val loaded = session.bind(intent, activity)
            threeDS = host
            authorizer = session.makeWalletAuthorizer(host, activity)
            pendingLauncher?.let { authorizer?.bindResolutionLauncher(it) }
            availability = GooglePayAvailability(
                available = loaded.googlePayAvailable,
                ready = loaded.googlePayReady,
                allowedPaymentMethodsJson = loaded.googlePayAllowedPaymentMethods,
                orderInfo = loaded.orderInfo,
            )
            isOrderConsumed = session.isOrderConsumed
            isProcessing = session.isProcessing
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
            throw e
        } catch (e: Exception) {
            isProcessing = false
            onResult(
                PaymentResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD),
            )
        }
    }

    fun startPayment() {
        if (!session.isInteractionEnabled) return
        val wallet = authorizer ?: return
        isProcessing = true
        activity.lifecycleScope.launch {
            val result = session.startWallet(wallet)
            isProcessing = session.isProcessing
            isOrderConsumed = session.isOrderConsumed
            if (result.status == PaymentResult.Status.CANCELED && !session.isOrderConsumed) {
                onEvent(GooglePayEvent.Processing(false))
                return@launch
            }
            onEvent(GooglePayEvent.Processing(false))
            onResult(result)
        }
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
    val controller = remember(configuration, activity) {
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

@Composable
fun GooglePayButton(
    controller: GooglePayController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (GooglePayEvent) -> Unit = {},
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(controller, intent) {
        controller.bind(intent) { currentOnEvent(it) }
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
