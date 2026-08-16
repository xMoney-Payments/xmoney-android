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
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.DigitalWalletFactory
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
 * the surface stays mounted but unusable until [bind] with a new order.
 */
class EmbeddedPaymentController(
    private val configuration: PaymentConfig,
    private val activity: FragmentActivity,
    private val onResult: (PaymentResult) -> Unit,
) {
    private val session = PaymentSession(configuration, placeholderIntent(), activity.applicationContext)

    var sheetState: SheetState? by mutableStateOf(null)
        private set
    var isProcessing: Boolean by mutableStateOf(false)
        private set
    var isOrderConsumed: Boolean by mutableStateOf(false)
        private set
    var paymentConfig: ResolvedPaymentConfig? by mutableStateOf(null)
        private set

    val googlePayAppearance: WalletAppearance
        get() = configuration.paymentMethods.googlePay.appearance

    val isInteractionEnabled: Boolean
        get() = session.isInteractionEnabled

    private var threeDS: ThreeDSHostController? = null
    private var authorizer: DigitalWalletAuthorizing? = null
    private var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingResolution: ActivityResult? = null
    private var onEvent: (EmbeddedEvent) -> Unit = {}
    private var boundIntent: PaymentIntent? = null

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

    suspend fun bind(
        order: PaymentIntent,
        onEvent: (EmbeddedEvent) -> Unit = {},
    ) {
        this.onEvent = onEvent
        boundIntent = order
        paymentConfig = configuration.resolve(order)
        val threeDSHost = ThreeDSHostController(activity) { configuration.options.locale }
        try {
            val loaded = session.bind(order, activity)
            threeDS = threeDSHost
            authorizer = session.makeWalletAuthorizer(threeDSHost, activity)
            resolutionLauncher?.let { authorizer?.bindResolutionLauncher(it) }
            sheetState = loaded
            isOrderConsumed = session.isOrderConsumed
            isProcessing = session.isProcessing
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
        if (!session.isInteractionEnabled) return
        val wallet = authorizer ?: return
        val presenter = threeDS ?: return
        if (DigitalWalletFactory.makeGooglePay == null) return
        updateProcessing(true)
        activity.lifecycleScope.launch {
            val result = session.startWallet(wallet)
            deliverResult(result)
        }
    }

    fun payWithCard(input: CardInput) {
        if (!session.isInteractionEnabled) return
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
        if (!session.isInteractionEnabled) return
        val presenter = threeDS ?: return
        runSubmission { session.submitSavedCard(card.id, presenter) }
    }

    suspend fun deleteSavedCard(card: SavedCard) {
        if (!session.isInteractionEnabled) return
        sheetState = session.deleteSavedCard(card.id)
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
        isProcessing = session.isProcessing
        isOrderConsumed = session.isOrderConsumed
        if (result.status != PaymentResult.Status.CANCELED || session.isOrderConsumed) {
            onEvent(EmbeddedEvent.Processing(false))
        } else {
            onEvent(EmbeddedEvent.Processing(false))
        }
        onResult(result)
    }

    private fun updateProcessing(processing: Boolean) {
        isProcessing = processing
        onEvent(EmbeddedEvent.Processing(processing))
    }

    companion object {
        private fun placeholderIntent(): PaymentIntent =
            PaymentIntent(
                com.xmoney.payments.model.OrderPayload(""),
                com.xmoney.payments.model.OrderChecksum(""),
            )
    }
}
