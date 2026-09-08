package com.xmoney.googlepay.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xmoney.googlepay.GooglePay
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.googlepay.ui.GooglePayButton
import com.xmoney.googlepay.ui.XCoinFlipLoader
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.threeds.ThreeDSHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class GooglePayHostActivity : FragmentActivity(), GooglePayCloseTarget, GooglePayOrderTarget {
    private lateinit var session: PaymentSession
    private lateinit var threeDS: ThreeDSHostController
    private lateinit var paymentConfig: PaymentConfig
    private lateinit var config: ResolvedPaymentConfig
    private var requestId: String = ""
    private var authorizer by mutableStateOf<DigitalWalletAuthorizing?>(null)

    private var didFinish = false
    private var isProcessing = false
    private var walletUiProcessing by mutableStateOf(false)
    private var isUpdatingOrder by mutableStateOf(false)
    private var sheetState by mutableStateOf<SheetState?>(null)
    private var bindError by mutableStateOf<String?>(null)
    private var pendingWalletResult: androidx.activity.result.ActivityResult? = null
    private var boundIntent: PaymentIntent? = null
    private var bindGeneration = 0

    private val resolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val wallet = authorizer
        if (wallet != null) {
            wallet.handleResolutionResult(result)
            if (wallet.hasPendingWalletAuthorization) {
                startPendingWallet(wallet)
            }
        } else {
            pendingWalletResult = result
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GooglePay.register()

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val sessionEntry = if (requestId.isNotBlank()) GooglePaySessionRegistry.get(requestId) else null
        val fromRegistry = sessionEntry?.config
        val fromIntent = intent.getParcelableExtra<GooglePayRequestParcelable>(EXTRA_CONFIG)?.toConfig()
        config = fromRegistry ?: fromIntent ?: run {
            finishWithResult(EngineResult.canceled())
            return
        }

        paymentConfig = PaymentConfig(
            publicKey = config.publicKey,
            card = config.card,
            paymentMethods = config.paymentMethods,
            options = config.options,
        )
        val initialIntent = PaymentIntent(
            OrderPayload(config.orderPayload),
            OrderChecksum(config.orderChecksum),
        )
        session = PaymentSession(paymentConfig, initialIntent, applicationContext)
        threeDS = ThreeDSHostController(this) { config.options.locale }

        if (requestId.isNotBlank()) {
            GooglePaySessionRegistry.bindHost(requestId, this)
            GooglePaySessionRegistry.bindCloseTarget(requestId, this)
            GooglePaySessionRegistry.bindOrderTarget(requestId, this)
        }
        if (didFinish) return

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requestClose()
                }
            },
        )

        setContent {
            GooglePayHostContent(
                state = sheetState,
                error = bindError,
                appearance = config.paymentMethods.googlePay.appearance,
                isUpdatingOrder = isUpdatingOrder,
                isWalletProcessing = walletUiProcessing,
                wallet = authorizer,
                onPay = { wallet ->
                    walletUiProcessing = true
                    startPendingWallet(wallet)
                },
            )
        }

        lifecycleScope.launch {
            try {
                bindOrder(initialIntent)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // bindOrder already finished the host
            }
        }
    }

    override fun requestClose(): Boolean {
        if (didFinish) return true
        if (isProcessing || !session.canDismiss) return false
        finishWithResult(EngineResult.canceled())
        return true
    }

    override suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: (GooglePayEvent) -> Unit,
    ) {
        if (didFinish) throw IllegalStateException("Google Pay is not presented")
        if (isProcessing) return
        if (boundIntent == intent && sheetState != null && !session.isOrderConsumed) {
            sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Ready)
            onEvent(GooglePayEvent.Ready)
            return
        }
        bindOrder(intent)
        if (didFinish) return
        onEvent(GooglePayEvent.Ready)
    }

    private suspend fun bindOrder(intent: PaymentIntent) {
        val generation = ++bindGeneration
        isUpdatingOrder = true
        try {
            val loaded = session.bind(intent, this)
            if (generation != bindGeneration || didFinish) throw CancellationException()
            val wallet = session.makeWalletAuthorizer(threeDS, this)
            if (wallet == null || !loaded.googlePayAvailable ||
                loaded.googlePayAllowedPaymentMethods.isNullOrBlank()
            ) {
                if (generation != bindGeneration) throw CancellationException()
                finishWithResult(
                    EngineResult.failed("GOOGLE_PAY", "Google Pay is not available"),
                )
                return
            }
            wallet.bindResolutionLauncher(resolutionLauncher)
            authorizer = wallet
            boundIntent = intent
            config = paymentConfig.resolve(intent)
            sessionOrEmpty()?.apply {
                this.config = config
                isAvailable = loaded.googlePayAvailable
                isReady = loaded.googlePayReady
            }
            sheetState = loaded
            isUpdatingOrder = false
            sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Ready)
            val pending = pendingWalletResult
            pendingWalletResult = null
            if (pending != null) {
                wallet.handleResolutionResult(pending)
                if (pending.resultCode == Activity.RESULT_OK &&
                    wallet.hasPendingWalletAuthorization
                ) {
                    startPendingWallet(wallet)
                }
            }
        } catch (e: CancellationException) {
            if (generation == bindGeneration) {
                isUpdatingOrder = false
            }
            throw e
        } catch (e: Exception) {
            if (generation != bindGeneration) throw CancellationException()
            isUpdatingOrder = false
            val message = e.message ?: PaymentError.GENERIC_LOAD
            bindError = message
            finishWithResult(EngineResult.failed("LOAD_ERROR", message))
            throw e
        }
    }

    private fun startPendingWallet(wallet: DigitalWalletAuthorizing) {
        if (isProcessing || isUpdatingOrder || !session.isInteractionEnabled) return
        isProcessing = true
        sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Processing(true))
        lifecycleScope.launch {
            val result = session.startWallet(wallet)
            isProcessing = session.isProcessing
            sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Processing(false))
            walletUiProcessing = false
            finishWithResult(result)
        }
    }

    private fun sessionOrEmpty(): GooglePaySessionRegistry.Session? =
        if (requestId.isBlank()) null else GooglePaySessionRegistry.get(requestId)

    private fun finishWithResult(result: EngineResult) {
        if (didFinish) return
        didFinish = true
        val registered = sessionOrEmpty()
        registered?.onResult?.invoke(OrderConsumption.merchantResult(result))
        if (requestId.isNotBlank()) {
            GooglePaySessionRegistry.remove(requestId)
        }
        finish()
    }

    companion object {
        const val EXTRA_CONFIG = "xmoney_google_pay_config"
        const val EXTRA_REQUEST_ID = "xmoney_google_pay_request_id"

        fun createIntent(
            context: Context,
            requestId: String,
            config: ResolvedPaymentConfig,
        ): Intent =
            Intent(context, GooglePayHostActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_CONFIG, GooglePayRequestParcelable.from(config))
            }
    }
}

@Composable
private fun GooglePayHostContent(
    state: SheetState?,
    error: String?,
    appearance: WalletAppearance,
    isUpdatingOrder: Boolean,
    isWalletProcessing: Boolean,
    wallet: DigitalWalletAuthorizing?,
    onPay: (DigitalWalletAuthorizing) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(error, color = Color.White)
            state == null -> XCoinFlipLoader(color = Color(0xFF7C4DFF))
            else -> {
                val methods = state.googlePayAllowedPaymentMethods
                if (methods.isNullOrBlank() || wallet == null) {
                    Text("Google Pay is not available", color = Color.White)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp),
                    ) {
                        GooglePayButton(
                            appearance = appearance,
                            allowedPaymentMethods = methods,
                            enabled = !isWalletProcessing && !isUpdatingOrder &&
                                state.googlePayReady != false,
                            onClick = { onPay(wallet) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Parcelize
data class GooglePayRequestParcelable(
    val config: ResolvedPaymentConfig,
) : Parcelable {
    fun toConfig(): ResolvedPaymentConfig = config

    companion object {
        fun from(config: ResolvedPaymentConfig) =
            GooglePayRequestParcelable(config)
    }
}
