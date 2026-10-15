package com.xmoney.paymentelement

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.VisibleForTesting
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
import com.xmoney.payments.engine.PendingPaymentStore
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.payments.model.UpdateOrderSuperseded
import com.xmoney.payments.util.onMainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
    @VisibleForTesting
    internal var session = PaymentSession(liveConfiguration, placeholderIntent(), activity.applicationContext)

    var sheetState: SheetState? by mutableStateOf(null)
        private set
    var isProcessing: Boolean by mutableStateOf(false)
        private set
    var isOrderConsumed: Boolean by mutableStateOf(false)
        private set
    /**
     * False during [updateOrder], an in-flight charge, after the order is consumed,
     * or while the bound order is stale because a later bind failed.
     */
    var isInteractionEnabled: Boolean by mutableStateOf(true)
        private set
    var paymentConfig: ResolvedPaymentConfig? by mutableStateOf(null)
        private set

    val googlePayAppearance: WalletAppearance
        get() = liveConfiguration.paymentMethods.googlePay.appearance

    internal var isUpdatingOrder by mutableStateOf(false)
        private set
    internal var bindError: String? by mutableStateOf(null)
        private set
    /** A later bind failed; Pay stays locked on the previous order until a bind succeeds. */
    internal var isStaleOrder by mutableStateOf(false)
        private set
    private var bindGeneration = 0
    private var createOrderTask: Deferred<PaymentIntent>? = null
    private var inFlightOrder: PaymentIntent? = null
    private var initialFailureReported = false
    private var threeDS: ThreeDSHostController? = null
    private var authorizer: DigitalWalletAuthorizing? = null
    private var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingResolution: ActivityResult? = null
    private var onEvent: (EmbeddedEvent) -> Unit = {}
    internal var boundIntent: PaymentIntent? = null
        private set
    private var submitHandler: (() -> Unit)? = null

    init {
        session.onPendingChanged = { pending ->
            boundIntent?.let { PendingPaymentStore.put(PaymentSession.orderKey(it), pending) }
        }
    }

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
     * does not emit [EmbeddedEvent.Processing] or [EmbeddedEvent.Ready].
     * [PaymentElement] emits [EmbeddedEvent.Ready] after the surface draws.
     *
     * The latest call wins: it supersedes any in-flight [updateOrder], which then
     * throws [UpdateOrderSuperseded], even when [order] is the order already bound.
     * The new order is installed only after it loads. Pay is not locked while you
     * create [order] on your server; pass that call to the `createOrder` overload
     * to lock it too.
     *
     * While a payment is in progress, the bound order is a no-op and any other
     * order throws [PaymentError.PaymentInProgress]. If binding fails after an
     * order was already bound, Pay stays locked on that stale order,
     * [EmbeddedEvent.UpdateFailed] is emitted, the error is thrown, and
     * [onResult] is not called. Runs on the main thread.
     */
    suspend fun updateOrder(
        order: PaymentIntent,
        onEvent: ((EmbeddedEvent) -> Unit)? = null,
    ): Unit = onMainThread {
        onEvent?.let { this@EmbeddedPaymentController.onEvent = it }
        if (isProcessing) {
            if (isBound(order)) return@onMainThread
            throw PaymentError.PaymentInProgress()
        }
        val generation = supersede()
        if (isBound(order)) {
            paymentConfig = liveConfiguration.resolve(order)
            isStaleOrder = false
            applyUpdatingOrder(false)
            return@onMainThread
        }
        applyUpdatingOrder(true)
        bindLocked(generation, order)
    }

    /**
     * Lock Pay, create the next order with [createOrder], bind it, and return it.
     *
     * Use this when the shopper changes the cart. Pay, [confirm], and Google Pay
     * are no-ops from this call until the new order is bound, including while
     * [createOrder] talks to your server, so a fast Pay tap cannot charge the
     * previous order. If [createOrder] throws, the previous order stays bound and
     * payable, the error is thrown, and no [EmbeddedEvent.UpdateFailed] is emitted.
     * A newer [updateOrder] supersedes this one ([UpdateOrderSuperseded]) and
     * cancels [createOrder]; cancelling the caller cancels it too.
     *
     * [createOrder] runs on the main thread, so it must be a non-blocking suspend call.
     */
    suspend fun updateOrder(
        onEvent: ((EmbeddedEvent) -> Unit)? = null,
        createOrder: suspend () -> PaymentIntent,
    ): PaymentIntent = onMainThread {
        onEvent?.let { this@EmbeddedPaymentController.onEvent = it }
        if (isProcessing) throw PaymentError.PaymentInProgress()
        val generation = supersede()
        applyUpdatingOrder(true)
        val task = async { createOrder() }
        createOrderTask = task
        val order = try {
            task.await()
        } catch (e: Throwable) {
            if (generation != bindGeneration) throw UpdateOrderSuperseded()
            applyUpdatingOrder(false)
            throw e
        } finally {
            if (createOrderTask === task) createOrderTask = null
        }
        if (generation != bindGeneration) throw UpdateOrderSuperseded()
        if (isBound(order)) {
            paymentConfig = liveConfiguration.resolve(order)
            isStaleOrder = false
            applyUpdatingOrder(false)
            return@onMainThread order
        }
        bindLocked(generation, order)
        order
    }

    /**
     * Surface-driven rebind from [PaymentElement]. The bound order, or the order an
     * in-flight merchant call is already binding, is a no-op that cancels nothing.
     */
    internal suspend fun rebindFromSurface(
        order: PaymentIntent,
        onEvent: (EmbeddedEvent) -> Unit,
    ) {
        this.onEvent = onEvent
        if (isBound(order) || inFlightOrder == order) {
            if (isBound(order)) paymentConfig = liveConfiguration.resolve(order)
            return
        }
        updateOrder(order)
    }

    internal fun resolvedConfig(order: PaymentIntent): ResolvedPaymentConfig = liveConfiguration.resolve(order)

    private fun supersede(): Int {
        val generation = ++bindGeneration
        createOrderTask?.cancel()
        createOrderTask = null
        inFlightOrder = null
        return generation
    }

    private fun isBound(order: PaymentIntent): Boolean =
        boundIntent == order && sheetState != null && !isOrderConsumed && session.isBound(order)

    private suspend fun bindLocked(generation: Int, order: PaymentIntent) {
        val initial = sheetState == null
        val threeDSHost = ThreeDSHostController(activity) { liveConfiguration.options.locale }
        inFlightOrder = order
        try {
            val candidate = session.load(order, activity)
            if (generation != bindGeneration) throw UpdateOrderSuperseded()
            session.commit(candidate)
            inFlightOrder = null
            bindError = null
            isStaleOrder = false
            initialFailureReported = false
            boundIntent = order
            paymentConfig = liveConfiguration.resolve(order)
            threeDS = threeDSHost
            authorizer = session.makeWalletAuthorizer(threeDSHost, activity)
            resolutionLauncher?.let { authorizer?.bindResolutionLauncher(it) }
            sheetState = candidate.state
            isOrderConsumed = session.isOrderConsumed
            isProcessing = session.isProcessing
            applyUpdatingOrder(false)
            val unsettled = PendingPaymentStore.get(PaymentSession.orderKey(order))
            if (unsettled != null) {
                runSubmission { session.recover(unsettled) }
                return
            }
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
            if (!initial) onEvent(EmbeddedEvent.UpdateFailed(error))
            throw e
        }
    }

    @VisibleForTesting
    internal fun setProcessingForTest(processing: Boolean) {
        isProcessing = processing
        syncInteractionEnabled()
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
