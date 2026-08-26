package com.xmoney.googlepay.internal

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.threeds.ThreeDSHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class GooglePayHostActivity : FragmentActivity(), GooglePayCloseTarget {
    private lateinit var session: PaymentSession
    private lateinit var threeDS: ThreeDSHostController
    private lateinit var config: ResolvedPaymentConfig
    private var requestId: String = ""
    private var authorizer: DigitalWalletAuthorizing? = null

    private var didFinish = false
    private var isProcessing = false
    private var walletUiProcessing by mutableStateOf(false)
    private var pendingWalletResult: androidx.activity.result.ActivityResult? = null

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

        if (requestId.isNotBlank()) {
            GooglePaySessionRegistry.bindHost(requestId, this)
            GooglePaySessionRegistry.bindCloseTarget(requestId, this)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requestClose()
                }
            },
        )

        val paymentConfig = PaymentConfig(
            publicKey = config.publicKey,
            card = config.card,
            paymentMethods = config.paymentMethods,
            options = config.options,
        )
        val intent = PaymentIntent(
            OrderPayload(config.orderPayload),
            OrderChecksum(config.orderChecksum),
        )
        session = PaymentSession(paymentConfig, intent, applicationContext)
        threeDS = ThreeDSHostController(this) { config.options.locale }

        setContent {
            GooglePayHostContent(
                session = session,
                activity = this,
                config = config,
                threeDS = threeDS,
                onAuthorizer = { wallet ->
                    authorizer = wallet
                    wallet.bindResolutionLauncher(resolutionLauncher)
                    val pending = pendingWalletResult
                    pendingWalletResult = null
                    if (pending != null) {
                        wallet.handleResolutionResult(pending)
                        if (pending.resultCode == android.app.Activity.RESULT_OK &&
                            wallet.hasPendingWalletAuthorization
                        ) {
                            startPendingWallet(wallet)
                        }
                    }
                },
                onBound = { available, ready ->
                    sessionOrEmpty()?.apply {
                        isAvailable = available
                        isReady = ready
                    }
                },
                onReady = {
                    sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Ready)
                },
                onUnavailable = {
                    finishWithResult(
                        EngineResult.failed("GOOGLE_PAY", "Google Pay is not available"),
                    )
                },
                onFailed = { message ->
                    finishWithResult(EngineResult.failed("LOAD_ERROR", message))
                },
                isWalletProcessing = walletUiProcessing,
                onPay = { wallet ->
                    walletUiProcessing = true
                    startPendingWallet(wallet)
                },
            )
        }
    }

    override fun requestClose() {
        if (isProcessing || !session.canDismiss) return
        finishWithResult(EngineResult.canceled())
    }

    private fun startPendingWallet(wallet: DigitalWalletAuthorizing) {
        if (isProcessing || !session.isInteractionEnabled) return
        isProcessing = true
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
    session: PaymentSession,
    activity: FragmentActivity,
    config: ResolvedPaymentConfig,
    threeDS: ThreeDSHostController,
    onAuthorizer: (DigitalWalletAuthorizing) -> Unit,
    onBound: (available: Boolean, ready: Boolean) -> Unit,
    onReady: () -> Unit,
    onUnavailable: () -> Unit,
    onFailed: (String) -> Unit,
    isWalletProcessing: Boolean,
    onPay: (DigitalWalletAuthorizing) -> Unit,
) {
    var state by remember { mutableStateOf<SheetState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notifiedReady by remember { mutableStateOf(false) }
    var wallet by remember { mutableStateOf<DigitalWalletAuthorizing?>(null) }

    val intent = PaymentIntent(
        OrderPayload(config.orderPayload),
        OrderChecksum(config.orderChecksum),
    )

    LaunchedEffect(Unit) {
        try {
            val loaded = session.bind(intent, activity)
            val authorizer = session.makeWalletAuthorizer(threeDS, activity)
            if (authorizer == null || !loaded.googlePayAvailable ||
                loaded.googlePayAllowedPaymentMethods.isNullOrBlank()
            ) {
                onUnavailable()
                return@LaunchedEffect
            }
            wallet = authorizer
            onAuthorizer(authorizer)
            onBound(loaded.googlePayAvailable, loaded.googlePayReady)
            state = loaded
            if (!notifiedReady) {
                notifiedReady = true
                onReady()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "Failed to load Google Pay"
            error = message
            onFailed(message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(error!!, color = Color.White)
            state == null -> XCoinFlipLoader(color = Color(0xFF7C4DFF))
            else -> {
                val methods = state?.googlePayAllowedPaymentMethods
                val currentWallet = wallet
                if (methods.isNullOrBlank() || currentWallet == null) {
                    Text("Google Pay is not available", color = Color.White)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp),
                    ) {
                        GooglePayButton(
                            appearance = config.paymentMethods.googlePay.appearance,
                            allowedPaymentMethods = methods,
                            enabled = !isWalletProcessing && state?.googlePayReady != false,
                            onClick = { onPay(currentWallet) },
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
