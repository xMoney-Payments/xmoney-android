package com.xmoney.payments.engine

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.CancellationException

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

    private var boundKey: String? = null
    private var bindGeneration: Int = 0

    init {
        injectCardHolderVerification()
    }

    suspend fun bind(intent: PaymentIntent, activity: FragmentActivity? = null): SheetState {
        val key = key(intent)
        state?.let { current ->
            if (boundKey == key) return current
        }

        val generation = ++bindGeneration
        val resolved = configuration.resolve(intent)
        val engineToLoad = if (key(engine.config.orderPayload, engine.config.orderChecksum) == key) {
            engine
        } else {
            PaymentEngine(resolved, appContext, http).also { next ->
                configuration.card.cardHolderVerification?.onCardHolderVerification?.let {
                    next.onCardHolderVerification = it
                }
            }
        }

        val googlePayConfigured = configuration.paymentMethods.googlePay.enabled &&
            DigitalWalletFactory.makeGooglePay != null
        var loaded = engineToLoad.load(googlePayConfigured)
        val decorate = DigitalWalletFactory.decorateSheetState
        if (decorate != null && loaded.googlePayAvailable && activity != null) {
            loaded = decorate(engineToLoad, loaded, activity)
        }
        if (generation != bindGeneration) {
            throw CancellationException()
        }

        engine = engineToLoad
        boundKey = key
        state = loaded
        isOrderConsumed = false
        isProcessing = false
        return loaded
    }

    suspend fun submitNewCard(input: CardInput, presenter: ThreeDSPresenter): EngineResult =
        submit(didAuthorize = true) { engine.submitNewCard(input, presenter) }

    suspend fun submitSavedCard(cardId: String, presenter: ThreeDSPresenter): EngineResult =
        submit(didAuthorize = true) { engine.submitSavedCard(cardId, presenter) }

    suspend fun startWallet(authorizer: DigitalWalletAuthorizing): EngineResult {
        if (!beginOperation()) return canceled
        val result = authorizer.start()
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
        return try {
            val result = operation()
            finish(result, didAuthorize)
            result
        } catch (e: CancellationException) {
            isProcessing = false
            canceled
        } catch (e: PaymentError) {
            finishFailed(e)
        } catch (e: Exception) {
            finishFailed(PaymentError.Payment(e.message ?: PaymentError.GENERIC_PAYMENT))
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
