package com.xmoney.googlepay.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
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
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.googlepay.ui.GooglePayButton
import com.xmoney.googlepay.ui.XCoinFlipLoader
import kotlinx.parcelize.Parcelize

class GooglePayHostActivity : FragmentActivity() {
    private val googlePayController = GooglePayWalletController(this)
    private lateinit var engine: PaymentEngine
    private lateinit var threeDS: ThreeDSHostController
    private lateinit var config: ResolvedPaymentConfig
    private var requestId: String = ""

    private var didFinish = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val session = if (requestId.isNotBlank()) GooglePaySessionRegistry.get(requestId) else null
        val fromRegistry = session?.config
        val fromIntent = intent.getParcelableExtra<GooglePayRequestParcelable>(EXTRA_CONFIG)?.toConfig()
        config = fromRegistry ?: fromIntent ?: run {
            finishWithResult(PaymentResult(PaymentResult.Status.CANCELED, null, null, null))
            return
        }

        if (requestId.isNotBlank()) {
            GooglePaySessionRegistry.bindHost(requestId, this)
        }

        engine = PaymentEngine(config, applicationContext)
        threeDS = ThreeDSHostController(this) { config.options.locale }

        googlePayController.attach(
            activity = this,
            engine = engine,
            threeDSPresenter = threeDS,
            scope = lifecycleScope,
            onProcessing = { processing ->
                sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Processing(processing))
            },
            onResult = { result -> finishWithResult(result) },
        )

        setContent {
            GooglePayHostContent(
                controller = googlePayController,
                config = config,
                onReady = {
                    sessionOrEmpty()?.onEvent?.invoke(GooglePayEvent.Ready)
                },
                onUnavailable = {
                    finishWithResult(
                        PaymentResult.failed("GOOGLE_PAY", "Google Pay is not available"),
                    )
                },
                onFailed = { message ->
                    finishWithResult(PaymentResult.failed("LOAD_ERROR", message))
                },
            )
        }
    }

    private fun sessionOrEmpty(): GooglePaySessionRegistry.Session? =
        if (requestId.isBlank()) null else GooglePaySessionRegistry.get(requestId)

    private fun finishWithResult(result: PaymentResult) {
        if (didFinish) return
        didFinish = true
        val session = sessionOrEmpty()
        session?.onResult?.invoke(result)
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
    controller: GooglePayWalletController,
    config: ResolvedPaymentConfig,
    onReady: () -> Unit,
    onUnavailable: () -> Unit,
    onFailed: (String) -> Unit,
) {
    var availability by remember { mutableStateOf<GooglePayAvailability?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var notifiedReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val prepared = controller.prepare()
            availability = prepared
            if (!prepared.available || prepared.allowedPaymentMethodsJson.isNullOrBlank()) {
                onUnavailable()
            } else if (!notifiedReady) {
                notifiedReady = true
                onReady()
            }
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
            availability == null -> XCoinFlipLoader(color = Color.White)
            else -> {
                val methods = availability?.allowedPaymentMethodsJson
                val orderInfo = availability?.orderInfo
                if (methods.isNullOrBlank() || orderInfo == null) {
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
                            enabled = !isProcessing && availability?.ready != false,
                            onClick = {
                                isProcessing = true
                                controller.startPayment(orderInfo)
                            },
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
