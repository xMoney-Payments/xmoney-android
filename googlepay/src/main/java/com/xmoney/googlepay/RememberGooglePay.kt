package com.xmoney.googlepay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.googlepay.internal.GooglePayAvailability
import com.xmoney.googlepay.internal.GooglePayWalletController
import com.xmoney.googlepay.ui.GooglePayButton as GooglePayButtonWidget
import kotlinx.coroutines.CancellationException

/**
 * Compose-facing controller for embedding Google Pay in a merchant screen.
 *
 * After COMPLETE, FAILED, or post-submit CANCELED (e.g. 3DS abandon) the bound
 * order checksum is consumed — the button stays mounted but disabled until
 * [bind] with a new order.
 */
class GooglePayController internal constructor(
    private val configuration: PaymentConfig,
    private val activity: FragmentActivity,
    private val googlePayController: GooglePayWalletController,
    private val onResult: (PaymentResult) -> Unit,
) {
    internal var availability by mutableStateOf<GooglePayAvailability?>(null)
        private set
    internal var isProcessing by mutableStateOf(false)
        private set
    /** True after COMPLETE/FAILED/post-submit CANCELED for the current bound order (checksum expired). */
    var isOrderConsumed: Boolean by mutableStateOf(false)
        private set

    internal val appearance: WalletAppearance
        get() = configuration.paymentMethods.googlePay.appearance

    val isInteractionEnabled: Boolean
        get() = !isProcessing && !isOrderConsumed

    private var boundOrderKey: String? = null
    private var onEvent: (GooglePayEvent) -> Unit = {}

    /**
     * Prepares Google Pay for [order]. Binding a **new** order clears [isOrderConsumed].
     */
    suspend fun bind(
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit = {},
    ) {
        this.onEvent = onEvent
        val key = "${intent.orderPayload}:${intent.orderChecksum}"
        if (boundOrderKey == key && availability != null) return

        boundOrderKey = key
        isOrderConsumed = false
        isProcessing = false

        val paymentConfig = configuration.copy(
            paymentMethods = configuration.paymentMethods.copy(
                googlePay = configuration.paymentMethods.googlePay.copy(enabled = true),
            ),
        ).resolve(intent)
        val paymentEngine = PaymentEngine(paymentConfig, activity.applicationContext)
        val host = ThreeDSHostController(activity) { paymentConfig.options.locale }
        googlePayController.attach(
            activity = activity,
            engine = paymentEngine,
            threeDSPresenter = host,
            onProcessing = { processing ->
                if (!isOrderConsumed) {
                    isProcessing = processing
                    this.onEvent(GooglePayEvent.Processing(processing))
                }
            },
            onResult = { result -> deliverResult(result) },
            scope = activity.lifecycleScope,
        )
        try {
            availability = googlePayController.prepare()
            this.onEvent(GooglePayEvent.Ready)
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
        if (!isInteractionEnabled) return
        val orderInfo = availability?.orderInfo ?: return
        isProcessing = true
        googlePayController.startPayment(orderInfo)
    }

    private fun deliverResult(result: PaymentResult) {
        isProcessing = false
        when (result.status) {
            PaymentResult.Status.COMPLETE,
            PaymentResult.Status.FAILED,
            PaymentResult.Status.CANCELED,
            -> {
                isOrderConsumed = true
                onEvent(GooglePayEvent.Processing(false))
            }
        }
        onResult(result)
    }
}

@Composable
fun rememberGooglePay(
    configuration: PaymentConfig,
    onResult: (PaymentResult) -> Unit,
): GooglePayController {
    val activity = LocalContext.current as FragmentActivity
    val googlePayController = remember { GooglePayWalletController() }
    val currentOnResult = rememberUpdatedState(onResult)
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        googlePayController.handleResolutionResult(result)
    }
    LaunchedEffect(resolutionLauncher) {
        googlePayController.bindResolutionLauncher(resolutionLauncher)
    }
    return remember(configuration, activity) {
        GooglePayController(
            configuration = configuration,
            activity = activity,
            googlePayController = googlePayController,
            onResult = { currentOnResult.value(it) },
        )
    }
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
