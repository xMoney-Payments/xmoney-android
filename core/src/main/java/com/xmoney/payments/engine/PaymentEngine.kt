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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface ThreeDSPresenter {
    suspend fun presentThreeDS(url: String, returnUrlMatcher: (String) -> Boolean): Boolean
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
class PaymentEngine(val config: ResolvedPaymentConfig, context: Context) {
    val env: PaymentEnvironment = PaymentEnvironment.from(config.publicKey)
        ?: throw PaymentError.InvalidKey()

    private val appContext = context.applicationContext
    private val http = HttpClient.shared()
    private val account = AccountService(http, env)
    private val configService = ConfigService(http, env)
    private val cards = CardsService(http, env)
    private val wallets = DigitalWalletsService(http, env)
    private val payment = PaymentService(http, env, appContext)
    private val transactions = TransactionService(http, env)

    private var sessionToken: String = ""
    private var nameCheckValidationEnabled: Boolean = false
    private var cachedOrderInfo: OrderPayloadInfo? = null
    private var cachedBackUrlHost: String? = null
    private var backUrlHostResolved: Boolean = false

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

        val siteConfig = configTask.await()
        nameCheckValidationEnabled = siteConfig.nameCheckValidationEnabled

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

    suspend fun walletParams(walletType: String): WalletParams =
        wallets.getParams(walletType, sessionToken)

    suspend fun deleteSavedCard(cardId: String) = cards.deleteCard(cardId, sessionToken)

    private suspend fun submit(fields: Map<String, String>, presenter: ThreeDSPresenter): PaymentResult {
        val response = payment.confirmPayment(fields)
        val parsed = payment.parse(response)

        return when (val submission = parsed.submission) {
            is PaymentSubmissionResult.Needs3DS -> {
                val transactionId = requireTransactionIdForThreeDS(parsed.transactionId)
                val backUrl = backUrlHost()
                val matcher: (String) -> Boolean = { returnUrl -> matchesReturn(returnUrl, backUrl) }
                handleThreeDSWithBackgroundRefresh(
                    submission.url,
                    transactionId,
                    presenter,
                    matcher,
                )
            }
            is PaymentSubmissionResult.Redirect -> resolveByPolling(parsed.transactionId)
            is PaymentSubmissionResult.Transaction -> resolveByPolling(submission.id)
        }
    }

    private fun backUrlHost(): String? {
        if (backUrlHostResolved) return cachedBackUrlHost
        backUrlHostResolved = true
        cachedBackUrlHost = OrderPayloadDecoder.backUrlHost(config.orderPayload)
        return cachedBackUrlHost
    }

    private fun matchesReturn(returnUrl: String, backUrlHost: String?): Boolean {
        val uri = runCatching { android.net.Uri.parse(returnUrl) }.getOrNull() ?: return false
        if (backUrlHost != null && uri.host == backUrlHost) return true
        val names = uri.queryParameterNames
        return names.contains("result") || names.contains("status")
    }

    private suspend fun resolveByPolling(transactionId: String?): PaymentResult {
        if (transactionId == null) throw PaymentError.Payment("Missing transaction id")
        val tx = transactions.poll(transactionId, sessionToken)
        return resultFromTransaction(tx)
    }

    private suspend fun handleThreeDSWithBackgroundRefresh(
        url: String,
        transactionId: String,
        presenter: ThreeDSPresenter,
        returnUrlMatcher: (String) -> Boolean,
    ): PaymentResult = coroutineScope {
        val pollDeferred = async { resolveByPolling(transactionId) }
        val threeDSDeferred = async { presenter.presentThreeDS(url, returnUrlMatcher) }
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
