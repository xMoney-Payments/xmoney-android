package com.xmoney.payments.engine

import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.OrderPayloadDecoder
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentSubmissionResult
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.model.SiteConfig
import com.xmoney.payments.model.WalletParams
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.network.HttpClient
import com.xmoney.payments.service.AccountService
import com.xmoney.payments.service.CardsService
import com.xmoney.payments.service.ConfigService
import com.xmoney.payments.service.DigitalWalletsService
import com.xmoney.payments.service.PaymentService
import com.xmoney.payments.service.TransactionService

import android.content.Context
import androidx.annotation.RestrictTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface ThreeDSPresenter {
    suspend fun presentThreeDS(
        url: String,
        returnUrlMatcher: (String) -> Boolean,
        formMethod: String = "GET",
        params: Map<String, String> = emptyMap(),
        onShown: () -> Unit = {},
    ): Boolean
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
    private val account = AccountService(http, env)
    private val configService = ConfigService(http, env)
    private val cards = CardsService(http, env)
    private val wallets = DigitalWalletsService(http, env)
    private val payment = PaymentService(http, env, appContext)
    private val transactions = TransactionService(http, env)

    private var sessionToken: String = ""
    private var nameCheckValidationEnabled: Boolean = false
    private var cachedOrderInfo: OrderPayloadInfo? = null
    private var cachedBackUrl: java.net.URI? = null
    private var backUrlResolved: Boolean = false
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

        val siteConfig = configTask.await()
        nameCheckValidationEnabled = siteConfig.nameCheckValidationEnabled
        walletTask.await()

        SheetState(
            sessionToken = sessionToken,
            orderInfo = orderInfo,
            savedCards = cardsTask.await(),
            googlePayAvailable = config.paymentMethods.googlePay.enabled && googlePayConfigured,
            nameCheckValidationEnabled = nameCheckValidationEnabled,
        )
    }

    suspend fun refreshSavedCards(): List<SavedCard> =
        runCatching { cards.getCards(sessionToken) }.getOrDefault(emptyList())

    suspend fun submitNewCard(card: CardInput, presenter: ThreeDSPresenter): PaymentResult {
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
                return PaymentResult(
                    status = PaymentResult.Status.FAILED,
                    transaction = null,
                    errorCode = "CARD_HOLDER_VERIFICATION",
                    errorMessage = PaymentError.VERIFICATION_REJECTED,
                )
            }
        }
        val fields = payment.cardFields(card, config.orderPayload, config.orderChecksum)
        return submit(fields, presenter)
    }

    suspend fun submitSavedCard(cardId: String, presenter: ThreeDSPresenter): PaymentResult {
        val fields = payment.savedCardFields(cardId, config.orderPayload, config.orderChecksum)
        return submit(fields, presenter)
    }

    suspend fun submitWallet(walletType: String, token: String, presenter: ThreeDSPresenter): PaymentResult {
        val fields = payment.walletFields(walletType, token, config.orderPayload, config.orderChecksum)
        return submit(fields, presenter)
    }

    suspend fun walletParams(walletType: String): WalletParams {
        cachedWalletParams[walletType]?.let { return it }
        val params = wallets.getParams(walletType, sessionToken)
        cachedWalletParams[walletType] = params
        return params
    }

    suspend fun deleteSavedCard(cardId: String) = cards.deleteCard(cardId, sessionToken)

    private suspend fun submit(fields: Map<String, String>, presenter: ThreeDSPresenter): PaymentResult {
        val response = payment.confirmPayment(fields)
        val parsed = payment.parse(response)

        return when (val submission = parsed.submission) {
            is PaymentSubmissionResult.Needs3DS -> {
                val transactionId = requireTransactionIdForThreeDS(parsed.transactionId)
                val backUrl = backUrl()
                val matcher: (String) -> Boolean = { returnUrl ->
                    backUrl != null && OrderPayloadDecoder.matchesReturnURL(returnUrl, backUrl)
                }
                handleThreeDSWithBackgroundRefresh(
                    submission,
                    transactionId,
                    presenter,
                    matcher,
                )
            }
            is PaymentSubmissionResult.Redirect -> resolveByPolling(parsed.transactionId)
            is PaymentSubmissionResult.Transaction -> resolveByPolling(submission.id)
        }
    }

    private fun backUrl(): java.net.URI? {
        if (backUrlResolved) return cachedBackUrl
        backUrlResolved = true
        cachedBackUrl = OrderPayloadDecoder.backUrl(config.orderPayload)
        return cachedBackUrl
    }

    private suspend fun resolveByPolling(transactionId: String?): PaymentResult {
        if (transactionId == null) throw PaymentError.Payment("Missing transaction id")
        val tx = transactions.poll(transactionId, sessionToken)
        return resultFromTransaction(tx)
    }

    private suspend fun handleThreeDSWithBackgroundRefresh(
        challenge: PaymentSubmissionResult.Needs3DS,
        transactionId: String,
        presenter: ThreeDSPresenter,
        returnUrlMatcher: (String) -> Boolean,
    ): PaymentResult = coroutineScope {
        val shown = CompletableDeferred<Unit>()
        val threeDSDeferred = async {
            presenter.presentThreeDS(
                challenge.url,
                returnUrlMatcher,
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
                threeDSDeferred.onAwait { completed ->
                    if (!completed) {
                        reconcileCanceledThreeDS(
                            fetchTransaction = {
                                transactions.getTransaction(transactionId, sessionToken)
                            },
                            pollDeferred = pollDeferred,
                        )
                    } else {
                        pollDeferred.await()
                    }
                }
            }
        } finally {
            pollDeferred.cancel()
            threeDSDeferred.cancel()
        }
    }
}
