package com.xmoney.paymentsheet

import com.xmoney.paymentsheet.internal.PaymentSheetContract
import com.xmoney.paymentsheet.internal.PaymentSheetRequest

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry
import java.util.UUID

class PaymentSheetLauncher internal constructor(
    private val configuration: PaymentConfig,
    private val launchRequest: (PaymentSheetRequest) -> Unit,
) {
    private var lastRequestId: String? = null

    fun present(
        intent: PaymentIntent,
        onEvent: (PaymentSheetEvent) -> Unit = {},
    ) {
        val requestId = UUID.randomUUID().toString()
        lastRequestId = requestId
        val config = configuration.resolve(intent)
        CheckoutSessionRegistry.register(
            requestId,
            CheckoutSessionRegistry.Session(
                onEvent = onEvent,
                onResult = null,
                onCardHolderVerification =
                    configuration.card.cardHolderVerification?.onCardHolderVerification,
                config = config,
            ),
        )
        launchRequest(PaymentSheetRequest(config, requestId = requestId))
    }

    fun dismiss() {
        CheckoutSessionRegistry.finishHost(lastRequestId)
    }
}

/**
 * Remember a launcher for the payment sheet. Uses the Activity Result API so
 * the terminal [PaymentResult] survives process death.
 */
@Composable
fun rememberPaymentSheet(
    configuration: PaymentConfig,
    onResult: (PaymentResult) -> Unit,
): PaymentSheetLauncher {
    val contract = remember { PaymentSheetContract() }
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        onResult(result)
    }
    return remember(configuration) {
        PaymentSheetLauncher(
            configuration = configuration,
            launchRequest = { request -> launcher.launch(request) },
        )
    }
}
