package com.xmoney.payments.engine

import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.customerdetails.CustomerDetailsVisibility
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.OrderPayloadDecoder
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentSubmissionResult
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.model.SiteConfig
import com.xmoney.payments.model.WalletParams
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.network.HttpClient
import com.xmoney.payments.service.AccountService
import com.xmoney.payments.service.CardsService
import com.xmoney.payments.service.ConfigService
import com.xmoney.payments.service.DigitalWalletsService
import com.xmoney.payments.service.InlineCheckoutFieldsService
import com.xmoney.payments.service.PaymentService
import com.xmoney.payments.service.TransactionService
import com.xmoney.payments.util.DeviceMetadata

import android.content.Context
import android.util.Log
import androidx.annotation.RestrictTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface ThreeDSPresenter {
    suspend fun presentThreeDS(
        url: String,
        formMethod: String = "GET",
        params: Map<String, String> = emptyMap(),
        onShown: () -> Unit = {},
    ): ThreeDSChallengeEnd
    fun dismissThreeDS()
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class SheetState(
    val sessionToken: String,
    val orderInfo: OrderPayloadInfo,
    val savedCards: List<SavedCard>,
    val googlePayAvailable: Boolean,
    val googlePayAllowedPaymentMethods: String? = null,
    val googlePayReady: Boolean = false,
    val nameCheckValidationEnabled: Boolean = false,
    val customerDetailsVisibility: CustomerDetailsVisibility = CustomerDetailsVisibility.Inactive,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PaymentEngine(
    val config: ResolvedPaymentConfig,
    context: Context,
    http: HttpClient = HttpClient.shared(),
) {
    val env: PaymentEnvironment = PaymentEnvironment.from(config.publicKey)
        ?: throw PaymentError.InvalidKey()

    private val appContext = context.applicationContext

    init {
        DeviceMetadata.prepare(appContext)
    }

    private val account = AccountService(http, env)
    private val configService = ConfigService(http, env)
    private val cards = CardsService(http, env)
    private val wallets = DigitalWalletsService(http, env)
    private val payment = PaymentService(http, env, appContext)
    private val transactions = TransactionService(http, env)
    private val inlineFields = InlineCheckoutFieldsService(http, env)

    /**
     * Same value as [SheetState.customerDetailsVisibility]. Google Pay reads it here
     * because the wallet controller does not keep the sheet state.
     */
    var customerDetailsVisibility: CustomerDetailsVisibility = CustomerDetailsVisibility.Inactive
        private set

    private var sessionToken: String = ""
    private var nameCheckValidationEnabled: Boolean = false
    private var cachedOrderInfo: OrderPayloadInfo? = null
    private val cachedWalletParams = mutableMapOf<String, WalletParams>()

    var onCardHolderVerification: ((CardHolderVerificationResult) -> Boolean)? =
        config.card.cardHolderVerification?.onCardHolderVerification

    private fun orderInfo(): OrderPayloadInfo =
        cachedOrderInfo ?: OrderPayloadDecoder.info(config.orderPayload).also { cachedOrderInfo = it }

    suspend fun load(googlePayConfigured: Boolean): SheetState = coroutineScope {
        sessionToken = account.getSessionToken(config.orderPayload, config.orderChecksum)

        val orderInfo = orderInfo()
        val showSavedCards = config.card.savedCards.enabled && !orderInfo.isVerifyCard && !orderInfo.isRecurring

        val configTask = async {
            runCatching { configService.getSiteConfig(sessionToken) }.getOrDefault(SiteConfig())
        }
        val cardsTask = async {
            if (showSavedCards) runCatching { cards.getCards(sessionToken) }.getOrDefault(emptyList())
            else emptyList()
        }
        val walletTask = async {
            if (googlePayConfigured && config.paymentMethods.googlePay.enabled) {
                runCatching { walletParams("googlePay") }.getOrNull()
            } else {
                null
            }
        }
        val fieldsTask = async {
            if (config.customerDetails.anyEnabled) inlineFields.getFields(sessionToken) else emptyList()
        }

        val siteConfig = configTask.await()
        nameCheckValidationEnabled = siteConfig.nameCheckValidationEnabled
        walletTask.await()
        val visibility = CustomerDetailsVisibility.resolve(fieldsTask.await())
        customerDetailsVisibility = visibility
        if (config.customerDetails.anyEnabled && !visibility.isActive) {
            Log.w(
                "customer-details",
                "[customer-details] Collection is enabled but /inline-checkout/fields returned no requested fields.",
            )
        }

        SheetState(
            sessionToken = sessionToken,
            orderInfo = orderInfo,
            savedCards = cardsTask.await(),
            googlePayAvailable = config.paymentMethods.googlePay.enabled && googlePayConfigured,
            nameCheckValidationEnabled = nameCheckValidationEnabled,
            customerDetailsVisibility = visibility,
        )
    }

    suspend fun refreshSavedCards(): List<SavedCard> =
        runCatching { cards.getCards(sessionToken) }.getOrDefault(emptyList())

    suspend fun submitNewCard(
        card: CardInput,
        presenter: ThreeDSPresenter,
        customerDetails: Map<String, String>? = null,
    ): EngineResult {
        val verification = config.card.cardHolderVerification
        if (verification != null) {
            if (!nameCheckValidationEnabled) {
                throw PaymentError.CardHolderVerification(PaymentError.NAME_CHECK_NOT_ENABLED)
            }
            val currency = orderInfo().currency
                ?: throw PaymentError.Payment("Missing currency for card holder verification")
            val result = account.validateAccount(
                card = card,
                name = verification.name,
                currency = currency,
                sessionToken = sessionToken,
            )
            val callback = onCardHolderVerification ?: verification.onCardHolderVerification
            if (!callback(result)) {
                return EngineResult(
                    status = EngineResult.Status.FAILED,
                    transaction = null,
                    errorCode = "CARD_HOLDER_VERIFICATION",
                    errorMessage = PaymentError.VERIFICATION_REJECTED,
                )
            }
        }
        val fields = payment.cardFields(card, config.orderPayload, config.orderChecksum, customerDetails)
        return submit(fields, presenter)
    }

    suspend fun submitSavedCard(
        cardId: String,
        presenter: ThreeDSPresenter,
        customerDetails: Map<String, String>? = null,
    ): EngineResult {
        val fields = payment.savedCardFields(cardId, config.orderPayload, config.orderChecksum, customerDetails)
        return submit(fields, presenter)
    }

    suspend fun submitWallet(
        walletType: String,
        token: String,
        presenter: ThreeDSPresenter,
        customerDetails: Map<String, String>? = null,
    ): EngineResult {
        val fields = payment.walletFields(walletType, token, config.orderPayload, config.orderChecksum, customerDetails)
        return submit(fields, presenter)
    }

    suspend fun walletParams(walletType: String): WalletParams {
        cachedWalletParams[walletType]?.let { return it }
        val params = wallets.getParams(walletType, sessionToken)
        cachedWalletParams[walletType] = params
        return params
    }

    suspend fun deleteSavedCard(cardId: String) = cards.deleteCard(cardId, sessionToken)

    /**
     * Pending while the server has a transaction and the result is unknown. A cancellation, from a
     * destroyed screen for example, leaves it set so the host can [resume].
     */
    var onPending: ((PendingPayment?) -> Unit)? = null

    private suspend fun submit(fields: Map<String, String>, presenter: ThreeDSPresenter): EngineResult {
        val response = payment.confirmPayment(fields)
        val parsed = payment.parse(response)

        val submission = parsed.submission
        val pendingId = when (submission) {
            is PaymentSubmissionResult.Transaction -> submission.id
            else -> parsed.transactionId
        }
        pendingId?.let { onPending?.invoke(PendingPayment(it, sessionToken)) }
        return settlePending {
            when (submission) {
                is PaymentSubmissionResult.Needs3DS -> {
                    val transactionId = requireTransactionIdForThreeDS(parsed.transactionId)
                    handleThreeDSWithBackgroundRefresh(
                        submission,
                        transactionId,
                        presenter,
                    )
                }
                is PaymentSubmissionResult.Redirect -> resolveByPolling(parsed.transactionId)
                is PaymentSubmissionResult.Transaction -> resolveByPolling(submission.id)
            }
        }
    }

    /** Polls a payment whose screen was destroyed. The challenge page is gone, so the wait is short. */
    suspend fun resume(pending: PendingPayment): EngineResult = settlePending {
        val tx = transactions.poll(
            pending.transactionId,
            pending.sessionToken,
            timeoutMs = RECOVERY_POLL_TIMEOUT_MS,
        )
        resultFromTransaction(tx)
    }

    private suspend fun settlePending(block: suspend () -> EngineResult): EngineResult {
        val result = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onPending?.invoke(null)
            throw e
        }
        onPending?.invoke(null)
        return result
    }

    private suspend fun resolveByPolling(transactionId: String?): EngineResult {
        if (transactionId == null) throw PaymentError.Payment("Missing transaction id")
        val tx = transactions.poll(transactionId, sessionToken)
        return resultFromTransaction(tx)
    }

    private suspend fun handleThreeDSWithBackgroundRefresh(
        challenge: PaymentSubmissionResult.Needs3DS,
        transactionId: String,
        presenter: ThreeDSPresenter,
    ): EngineResult = coroutineScope {
        val shown = CompletableDeferred<Unit>()
        val threeDSDeferred = async {
            presenter.presentThreeDS(
                challenge.url,
                challenge.formMethod,
                challenge.params,
                onShown = { shown.complete(Unit) },
            )
        }
        val pollDeferred = async {
            shown.await()
            resolveByPolling(transactionId)
        }
        try {
            select {
                pollDeferred.onAwait { result ->
                    presenter.dismissThreeDS()
                    threeDSDeferred.cancel()
                    result
                }
                threeDSDeferred.onAwait { end ->
                    when (val followUp = threeDSChallengeFollowUp(end)) {
                        ThreeDSChallengeFollowUp.WaitForPoll -> pollDeferred.await()
                        is ThreeDSChallengeFollowUp.ReconcileCancel -> reconcileCanceledThreeDS(
                            fetchTransaction = {
                                transactions.getTransaction(transactionId, sessionToken)
                            },
                            pollDeferred = pollDeferred,
                            graceMs = followUp.graceMs,
                        )
                        is ThreeDSChallengeFollowUp.ThrowUnlessComplete -> {
                            val tx = runCatching {
                                transactions.getTransaction(transactionId, sessionToken)
                            }.getOrNull()
                            pollDeferred.cancel()
                            if (tx != null && isTransactionComplete(tx)) {
                                resultFromTransaction(tx)
                            } else {
                                throw PaymentError.ThreeDS(followUp.message)
                            }
                        }
                        is ThreeDSChallengeFollowUp.WaitThenFail -> waitForPollOrFail(
                            fetchTransaction = {
                                transactions.getTransaction(transactionId, sessionToken)
                            },
                            pollDeferred = pollDeferred,
                            graceMs = followUp.graceMs,
                            message = followUp.message,
                        )
                    }
                }
            }
        } finally {
            pollDeferred.cancel()
            threeDSDeferred.cancel()
        }
    }
}
