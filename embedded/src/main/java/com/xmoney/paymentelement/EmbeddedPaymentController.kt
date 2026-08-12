package com.xmoney.paymentelement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.paymentelement.EmbeddedEvent
import com.xmoney.googlepay.internal.GooglePayWalletController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Orchestrates the embedded Payment Element: Google Pay, new card, and saved cards.
 *
 * After [PaymentResult.Status.COMPLETE], [PaymentResult.Status.FAILED], or
 * post-submit [PaymentResult.Status.CANCELED] (e.g. 3DS abandon) the bound order
 * checksum is consumed — the surface stays mounted but unusable until [bind] with a new order.
 */
class EmbeddedPaymentController(
    private val configuration: PaymentConfig,
    private val activity: FragmentActivity,
    private val googlePayController: GooglePayWalletController,
    private val onResult: (PaymentResult) -> Unit,
) {
    var sheetState: SheetState? by mutableStateOf(null)
        private set
    var isProcessing: Boolean by mutableStateOf(false)
        private set
    /** True after COMPLETE/FAILED/post-submit CANCELED for the current bound order (checksum expired). */
    var isOrderConsumed: Boolean by mutableStateOf(false)
        private set
    var paymentConfig: ResolvedPaymentConfig? by mutableStateOf(null)
        private set

    val googlePayAppearance: WalletAppearance
        get() = configuration.paymentMethods.googlePay.appearance

    val isInteractionEnabled: Boolean
        get() = !isProcessing && !isOrderConsumed

    private var boundKey: String? = null
    private var engine: PaymentEngine? = null
    private var threeDS: ThreeDSHostController? = null
    private var onEvent: (EmbeddedEvent) -> Unit = {}

    /**
     * Prepares the embedded surface for [order]. Call from [androidx.compose.runtime.LaunchedEffect].
     * Binding a **new** order clears [isOrderConsumed] so the form can be used again.
     */
    suspend fun bind(
        order: PaymentIntent,
        onEvent: (EmbeddedEvent) -> Unit = {},
    ) {
        this.onEvent = onEvent
        val key = "${order.orderPayload}:${order.orderChecksum}"
        if (boundKey == key && sheetState != null) {
            paymentConfig = configuration.resolve(order)
            return
        }

        val config = configuration.resolve(order)
        paymentConfig = config
        boundKey = key
        isOrderConsumed = false
        isProcessing = false

        val paymentEngine = PaymentEngine(config, activity.applicationContext)
        val threeDSHost = ThreeDSHostController(activity) { config.options.locale }
        engine = paymentEngine
        threeDS = threeDSHost

        googlePayController.attach(
            activity = activity,
            engine = paymentEngine,
            threeDSPresenter = threeDSHost,
            onProcessing = { processing ->
                if (!isOrderConsumed) {
                    isProcessing = processing
                    onEvent(EmbeddedEvent.Processing(processing))
                }
            },
            onResult = { result -> deliverResult(result) },
            scope = activity.lifecycleScope,
        )

        try {
            sheetState = if (config.paymentMethods.googlePay.enabled) {
                googlePayController.prepareSheetState()
            } else {
                paymentEngine.load(googlePayConfigured = false)
            }
            onEvent(EmbeddedEvent.Ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateProcessing(false)
            onResult(
                if (e is PaymentError) PaymentResult.failed(e)
                else PaymentResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD),
            )
        }
    }

    fun startGooglePay() {
        if (!isInteractionEnabled) return
        val orderInfo = sheetState?.orderInfo ?: return
        updateProcessing(true)
        googlePayController.startPayment(orderInfo)
    }

    fun payWithCard(input: CardInput) {
        if (!isInteractionEnabled) return
        val config = paymentConfig ?: return
        val invalid = CardFieldValidators.validateCardNumber(input.number) != null ||
            CardFieldValidators.validateExpiry(input.expiryMonth, input.expiryYear) != null ||
            CardFieldValidators.validateCVV(input.cvv) != null ||
            CardFieldValidators.validateHolderName(input.holderName) != null
        if (invalid) return
        val presenter = threeDS ?: return
        val paymentEngine = engine ?: return
        paymentEngine.onCardHolderVerification =
            config.card.cardHolderVerification?.onCardHolderVerification
        runSubmission { paymentEngine.submitNewCard(input, presenter) }
    }

    fun paySavedCard(card: SavedCard) {
        if (!isInteractionEnabled) return
        val presenter = threeDS ?: return
        runSubmission { engine!!.submitSavedCard(card.id, presenter) }
    }

    fun deleteSavedCard(card: SavedCard) {
        if (!isInteractionEnabled) return
        val paymentEngine = engine ?: return
        activity.lifecycleScope.launch {
            runCatching { paymentEngine.deleteSavedCard(card.id) }
            runCatching {
                val refreshed = paymentEngine.refreshSavedCards()
                sheetState = sheetState?.copy(savedCards = refreshed)
            }
        }
    }

    private fun runSubmission(block: suspend () -> PaymentResult) {
        updateProcessing(true)
        activity.lifecycleScope.launch {
            try {
                deliverResult(block())
            } catch (e: CancellationException) {
                updateProcessing(false)
                throw e
            } catch (e: PaymentError) {
                deliverResult(PaymentResult.failed(e))
            } catch (e: Exception) {
                deliverResult(PaymentResult.failed("PAYMENT_ERROR", e.message ?: PaymentError.GENERIC_PAYMENT))
            }
        }
    }

    private fun deliverResult(result: PaymentResult) {
        isProcessing = false
        when (result.status) {
            PaymentResult.Status.COMPLETE,
            PaymentResult.Status.FAILED,
            PaymentResult.Status.CANCELED,
            -> {
                isOrderConsumed = true
                onEvent(EmbeddedEvent.Processing(false))
            }
        }
        onResult(result)
    }

    private fun updateProcessing(processing: Boolean) {
        isProcessing = processing
        onEvent(EmbeddedEvent.Processing(processing))
    }
}
