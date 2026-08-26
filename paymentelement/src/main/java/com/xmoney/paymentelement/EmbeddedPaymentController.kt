package com.xmoney.paymentelement

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xmoney.payments.config.AppearanceConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.UserInterfaceStyle
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.DigitalWalletFactory
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.payments.validation.CardFieldValidators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Orchestrates the embedded Payment Element: Google Pay, new card, and saved cards.
 *
 * After COMPLETE, FAILED, or post-submit CANCELED the bound order is consumed —
 * the surface stays mounted but unusable until [updateOrder] with a new order.
 */
class EmbeddedPaymentController(
    configuration: PaymentConfig,
    private val activity: FragmentActivity,
    private val onResult: (PaymentResult) -> Unit,
) {
    private var liveConfiguration = configuration
    private val session = PaymentSession(liveConfiguration, placeholderIntent(), activity.applicationContext)

    var sheetState: SheetState? by mutableStateOf(null)
        private set
    var isProcessing: Boolean by mutableStateOf(false)
        private set
    var isOrderConsumed: Boolean by mutableStateOf(false)
        private set
    /** False during [updateOrder], an in-flight charge, or after the order is consumed. */
    var isInteractionEnabled: Boolean by mutableStateOf(true)
        private set
    var paymentConfig: ResolvedPaymentConfig? by mutableStateOf(null)
        private set

    val googlePayAppearance: WalletAppearance
        get() = liveConfiguration.paymentMethods.googlePay.appearance

    internal var isUpdatingOrder by mutableStateOf(false)
        private set
    private var bindGeneration = 0
    private var threeDS: ThreeDSHostController? = null
    private var authorizer: DigitalWalletAuthorizing? = null
    private var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingResolution: ActivityResult? = null
    private var onEvent: (EmbeddedEvent) -> Unit = {}
    private var boundIntent: PaymentIntent? = null
    private var submitHandler: (() -> Unit)? = null

    /**
     * Submit the currently selected method (new card or saved card).
     * Use with [com.xmoney.payments.config.SubmitButtonConfig.visible] = false
     * so the merchant owns the Pay CTA.
     */
    fun confirm() {
        if (!isInteractionEnabled) return
        submitHandler?.invoke()
    }

    internal fun bindSubmitHandler(handler: (() -> Unit)?) {
        submitHandler = handler
    }

    fun bindWalletResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        resolutionLauncher = launcher
        authorizer?.bindResolutionLauncher(launcher)
    }

    fun handleWalletResolution(result: ActivityResult) {
        val wallet = authorizer
        if (wallet == null) {
            pendingResolution = result
            return
        }
        wallet.handleResolutionResult(result)
        if (wallet.hasPendingWalletAuthorization) {
            startGooglePay()
        }
    }

    fun updateAppearance(appearance: AppearanceConfig) {
        liveConfiguration = liveConfiguration.copy(
            options = liveConfiguration.options.copy(appearance = appearance),
        )
        boundIntent?.let { paymentConfig = liveConfiguration.resolve(it) }
    }

    fun updateWalletAppearance(appearance: WalletAppearance) {
        liveConfiguration = liveConfiguration.copy(
            paymentMethods = liveConfiguration.paymentMethods.copy(
                googlePay = liveConfiguration.paymentMethods.googlePay.copy(appearance = appearance),
            ),
        )
        boundIntent?.let { paymentConfig = liveConfiguration.resolve(it) }
    }

    fun updateLocale(locale: String) {
        liveConfiguration = liveConfiguration.copy(
            options = liveConfiguration.options.copy(locale = locale),
        )
        boundIntent?.let { paymentConfig = liveConfiguration.resolve(it) }
    }

    fun updateStyle(style: UserInterfaceStyle) {
        liveConfiguration = liveConfiguration.copy(
            options = liveConfiguration.options.copy(style = style),
        )
        boundIntent?.let { paymentConfig = liveConfiguration.resolve(it) }
    }

    /**
     * Rebind a new signed order without tearing down the embedded surface.
     *
     * Pay, [confirm], and Google Pay are no-ops until this returns
     * ([isInteractionEnabled]). The Pay button keeps its current title — this
     * does not emit [EmbeddedEvent.Processing]. A newer [updateOrder] cancels
     * the in-flight one ([CancellationException]); the new intent is installed
     * only after success.
     */
    suspend fun updateOrder(
        order: PaymentIntent,
        onEvent: (EmbeddedEvent) -> Unit = {},
    ) {
        this.onEvent = onEvent
        if (boundIntent == order && sheetState != null && !isOrderConsumed) {
            paymentConfig = liveConfiguration.resolve(order)
            onEvent(EmbeddedEvent.Ready)
            return
        }
        val generation = ++bindGeneration
        applyUpdatingOrder(true)
        val threeDSHost = ThreeDSHostController(activity) { liveConfiguration.options.locale }
        try {
            val loaded = session.bind(order, activity)
            if (generation != bindGeneration) throw CancellationException()
            boundIntent = order
            paymentConfig = liveConfiguration.resolve(order)
            threeDS = threeDSHost
            authorizer = session.makeWalletAuthorizer(threeDSHost, activity)
            resolutionLauncher?.let { authorizer?.bindResolutionLauncher(it) }
            sheetState = loaded
            isOrderConsumed = session.isOrderConsumed
            isProcessing = session.isProcessing
            applyUpdatingOrder(false)
            onEvent(EmbeddedEvent.Ready)
            val pending = pendingResolution
            pendingResolution = null
            if (pending != null) {
                authorizer?.handleResolutionResult(pending)
                if (pending.resultCode == Activity.RESULT_OK &&
                    authorizer?.hasPendingWalletAuthorization == true
                ) {
                    startGooglePay()
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
                    if (e is PaymentError) EngineResult.failed(e)
                    else EngineResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD),
                ),
            )
            throw e
        }
    }

    fun startGooglePay() {
        if (!isInteractionEnabled) return
        val wallet = authorizer ?: return
        val presenter = threeDS ?: return
        if (DigitalWalletFactory.makeGooglePay == null) return
        updateProcessing(true)
        activity.lifecycleScope.launch {
            val result = session.startWallet(wallet)
            if (result.status == EngineResult.Status.CANCELED && !session.isOrderConsumed) {
                updateProcessing(false)
                return@launch
            }
            deliverResult(result)
        }
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
        session.onCardHolderVerification =
            config.card.cardHolderVerification?.onCardHolderVerification
        runSubmission { session.submitNewCard(input, presenter) }
    }

    fun paySavedCard(card: SavedCard) {
        if (!isInteractionEnabled) return
        val presenter = threeDS ?: return
        runSubmission { session.submitSavedCard(card.id, presenter) }
    }

    suspend fun deleteSavedCard(card: SavedCard) {
        if (!isInteractionEnabled) return
        sheetState = session.deleteSavedCard(card.id)
    }

    private fun runSubmission(block: suspend () -> EngineResult) {
        updateProcessing(true)
        activity.lifecycleScope.launch {
            try {
                deliverResult(block())
            } catch (e: CancellationException) {
                updateProcessing(false)
                throw e
            } catch (e: PaymentError) {
                deliverResult(EngineResult.failed(e))
            } catch (e: Exception) {
                deliverResult(EngineResult.failed("PAYMENT_ERROR", e.message ?: PaymentError.GENERIC_PAYMENT))
            }
        }
    }

    private fun deliverResult(result: EngineResult) {
        isProcessing = session.isProcessing
        isOrderConsumed = session.isOrderConsumed
        syncInteractionEnabled()
        onEvent(EmbeddedEvent.Processing(false))
        onResult(OrderConsumption.merchantResult(result))
    }

    private fun updateProcessing(processing: Boolean) {
        isProcessing = processing
        syncInteractionEnabled()
        onEvent(EmbeddedEvent.Processing(processing))
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
