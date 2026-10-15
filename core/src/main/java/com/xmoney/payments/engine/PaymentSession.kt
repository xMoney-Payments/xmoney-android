package com.xmoney.payments.engine

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.UpdateOrderSuperseded
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** A loaded order that [PaymentSession.commit] can make current. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class BindCandidate internal constructor(
    internal val key: String,
    internal val engine: PaymentEngine,
    val state: SheetState,
    internal val alreadyBound: Boolean,
)

/** Shared payment session for Sheet, Embedded, and standalone Google Pay. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PaymentSession(
    val configuration: PaymentConfig,
    intent: PaymentIntent,
    context: Context,
    private val http: HttpClient = HttpClient.shared(),
) {
    private val appContext = context.applicationContext
    var engine: PaymentEngine = PaymentEngine(configuration.resolve(intent), appContext, http)
        private set
    var state: SheetState? = null
        private set
    var isProcessing: Boolean = false
        private set
    var isOrderConsumed: Boolean = false
        private set

    val isInteractionEnabled: Boolean
        get() = !isProcessing && !isOrderConsumed

    val canDismiss: Boolean
        get() = !isProcessing

    var onCardHolderVerification: ((CardHolderVerificationResult) -> Boolean)?
        get() = engine.onCardHolderVerification
        set(value) {
            engine.onCardHolderVerification = value
        }

    /** Set while a submitted payment has no result yet. Hosts save it to [recover] after recreation. */
    var pendingPayment: PendingPayment? = null
        private set
    var onPendingChanged: ((PendingPayment?) -> Unit)? = null

    private var boundKey: String? = null
    private var bindGeneration: Int = 0
    /** A screen destroyed mid-payment cancels this; [recover] waits for it to unwind. */
    private var activeOperation: Job? = null

    init {
        injectCardHolderVerification()
        trackPending(engine)
    }

    private fun trackPending(target: PaymentEngine) {
        target.onPending = { pending ->
            pendingPayment = pending
            onPendingChanged?.invoke(pending)
        }
    }

    /** True when [intent] is the order this session last committed. */
    fun isBound(intent: PaymentIntent): Boolean = state != null && boundKey == key(intent)

    /** Load and commit in one step. A newer [bind] on this session cancels an older one. */
    suspend fun bind(intent: PaymentIntent, activity: FragmentActivity? = null): SheetState {
        val generation = ++bindGeneration
        val candidate = load(intent, activity)
        if (generation != bindGeneration) throw UpdateOrderSuperseded()
        commit(candidate)
        return candidate.state
    }

    /**
     * Loads [intent] without changing this session. Call [commit] only after the
     * caller has checked that its request is still current, so a superseded load
     * can never replace the order the shopper sees.
     */
    suspend fun load(intent: PaymentIntent, activity: FragmentActivity? = null): BindCandidate {
        val key = key(intent)
        state?.let { current ->
            if (boundKey == key) return BindCandidate(key, engine, current, alreadyBound = true)
        }

        val resolved = configuration.resolve(intent)
        val engineToLoad = if (key(engine.config.orderPayload, engine.config.orderChecksum) == key) {
            engine
        } else {
            PaymentEngine(resolved, appContext, http).also { next ->
                configuration.card.cardHolderVerification?.onCardHolderVerification?.let {
                    next.onCardHolderVerification = it
                }
                trackPending(next)
            }
        }

        val googlePayConfigured = configuration.paymentMethods.googlePay.enabled &&
            DigitalWalletFactory.makeGooglePay != null
        var loaded = engineToLoad.load(googlePayConfigured)
        val decorate = DigitalWalletFactory.decorateSheetState
        if (decorate != null && loaded.googlePayAvailable && activity != null) {
            loaded = decorate(engineToLoad, loaded, activity)
        }
        return BindCandidate(key, engineToLoad, loaded, alreadyBound = false)
    }

    /** Makes [candidate] the bound order. Re-committing the bound order keeps its consumed state. */
    fun commit(candidate: BindCandidate) {
        if (candidate.alreadyBound && boundKey == candidate.key && state != null) return
        engine = candidate.engine
        boundKey = candidate.key
        state = candidate.state
        isOrderConsumed = false
        isProcessing = false
    }

    /** Builds the wallet authorizer for a loaded but not yet committed [candidate]. */
    fun makeWalletAuthorizer(
        candidate: BindCandidate,
        presenter: ThreeDSPresenter,
        activity: FragmentActivity,
    ): DigitalWalletAuthorizing? =
        DigitalWalletFactory.makeGooglePay?.invoke(candidate.engine, presenter, activity)

    suspend fun submitNewCard(input: CardInput, presenter: ThreeDSPresenter): EngineResult =
        submit(didAuthorize = true) { engine.submitNewCard(input, presenter) }

    suspend fun submitSavedCard(cardId: String, presenter: ThreeDSPresenter): EngineResult =
        submit(didAuthorize = true) { engine.submitSavedCard(cardId, presenter) }

    /** Reports the result of a payment whose screen was destroyed, instead of taking a second payment. */
    suspend fun recover(pending: PendingPayment): EngineResult {
        activeOperation?.takeIf { it.isCancelled }?.join()
        return submit(didAuthorize = true) { engine.resume(pending) }
    }

    suspend fun startWallet(authorizer: DigitalWalletAuthorizing): EngineResult {
        if (!beginOperation()) return canceled
        val result = try {
            authorizer.start()
        } catch (e: CancellationException) {
            isProcessing = false
            throw e
        }
        finish(result, authorizer.didAuthorizePayment)
        return result
    }

    suspend fun deleteSavedCard(cardId: String): SheetState {
        engine.deleteSavedCard(cardId)
        val cards = engine.refreshSavedCards()
        val current = state ?: throw PaymentError.Load("Missing session state")
        val updated = current.copy(savedCards = cards)
        state = updated
        return updated
    }

    fun makeWalletAuthorizer(
        presenter: ThreeDSPresenter,
        activity: FragmentActivity,
    ): DigitalWalletAuthorizing? {
        state ?: return null
        return DigitalWalletFactory.makeGooglePay?.invoke(engine, presenter, activity)
    }

    private fun injectCardHolderVerification() {
        configuration.card.cardHolderVerification?.onCardHolderVerification?.let {
            engine.onCardHolderVerification = it
        }
    }

    private suspend fun submit(
        didAuthorize: Boolean,
        operation: suspend () -> EngineResult,
    ): EngineResult {
        if (!beginOperation()) return canceled
        val job = currentCoroutineContext()[Job]
        activeOperation = job
        return try {
            val result = operation()
            finish(result, didAuthorize)
            result
        } catch (e: CancellationException) {
            isProcessing = false
            throw e
        } catch (e: PaymentError) {
            finishFailed(e)
        } catch (e: Exception) {
            finishFailed(PaymentError.Payment(e.message ?: PaymentError.GENERIC_PAYMENT))
        } finally {
            if (activeOperation === job) activeOperation = null
        }
    }

    private fun beginOperation(): Boolean {
        if (isProcessing || isOrderConsumed) return false
        isProcessing = true
        return true
    }

    private fun finish(result: EngineResult, didAuthorize: Boolean) {
        isProcessing = false
        if (OrderConsumption.shouldConsume(result.status, didAuthorize)) {
            isOrderConsumed = true
        }
    }

    private fun finishFailed(error: PaymentError): EngineResult {
        val failed = EngineResult.failed(error)
        finish(failed, didAuthorize = true)
        return failed
    }

    companion object {
        /** Identifies a signed order, for [PendingPaymentStore]. */
        fun orderKey(intent: PaymentIntent): String = key(intent)

        private fun key(intent: PaymentIntent): String =
            key(intent.orderPayload, intent.orderChecksum)

        private fun key(payload: String, checksum: String): String = "$payload:$checksum"

        private val canceled = EngineResult(
            status = EngineResult.Status.CANCELED,
            transaction = null,
            errorCode = null,
            errorMessage = null,
        )
    }
}
