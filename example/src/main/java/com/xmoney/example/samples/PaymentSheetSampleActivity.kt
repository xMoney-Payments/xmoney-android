package com.xmoney.example.samples

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.BuildConfig
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.exampleAppearance
import com.xmoney.example.exampleForcedStyle
import com.xmoney.example.exampleWalletAppearance
import com.xmoney.example.orderConsumed
import com.xmoney.example.theme.ExampleTheme
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.SampleOrderCard
import com.xmoney.example.ui.SampleScaffold
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.launch

/**
 * Minimal Payment Sheet integration.
 *
 * Copy this file as a starting point. In production, replace [DemoCheckoutBackend]
 * with a call to **your** server that returns `payload` + `checksum` only.
 * Never ship a secret API key in the Android app.
 *
 * After COMPLETE, FAILED, or post-submit CANCELED the order checksum is consumed —
 * create a new [PaymentIntent] before presenting again. Cancel **before** pay
 * (header close) does not consume; present the same intent again.
 */
class PaymentSheetSampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                PaymentSheetSampleScreen(activity = this)
            }
        }
    }
}

@Composable
private fun PaymentSheetSampleScreen(activity: FragmentActivity) {
    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var heldIntent by remember { mutableStateOf<PaymentIntent?>(null) }
    var didProcess by remember { mutableStateOf(false) }
    var consumed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val style = exampleForcedStyle()
    val wallet = exampleWalletAppearance()
    val configuration = remember(style, wallet.color) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(enabled = true, appearance = wallet),
            ),
            card = CardConfig(
                savedCards = SavedCardsConfig(enabled = true),
            ),
            options = OptionsConfig(
                style = style,
                appearance = exampleAppearance(),
            ),
        )
    }

    val paymentSheet = rememberPaymentSheet(
        configuration = configuration,
        onResult = { result ->
            lastResult = result
            loading = false
            consumed = orderConsumed(result, didProcess)
            if (consumed) heldIntent = null
        },
    )

    fun present(intent: PaymentIntent) {
        didProcess = false
        heldIntent = intent
        paymentSheet.present(intent) { event ->
            when (event) {
                PaymentSheetEvent.Ready -> loading = false
                is PaymentSheetEvent.Processing -> {
                    if (event.isProcessing) didProcess = true
                }
            }
        }
    }

    SampleScaffold(
        title = "Payment Sheet",
        subtitle = "SDK owns the full checkout UI.",
        activity = activity,
        showTestCards = true,
    ) {
        if (!consumed) {
            SampleOrderCard()
            ExampleButton(
                label = if (lastResult is PaymentResult.Canceled) "Continue" else "Pay",
                loading = loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        lastResult = null
                        try {
                            val intent = heldIntent ?: DemoCheckoutBackend.createPaymentIntent()
                            present(intent)
                        } catch (e: Exception) {
                            error = e.message ?: "Could not create order"
                            loading = false
                        }
                    }
                },
            )
            if (lastResult is PaymentResult.Canceled) {
                ExampleStatusChip("You closed checkout before finishing.", ExampleStatusKind.Neutral)
            }
        }
        error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
        if (consumed) {
            lastResult?.let { result ->
                ExampleResultPanel(result)
                ExampleButton(
                    label = "New payment",
                    variant = ExampleButtonVariant.Secondary,
                    onClick = {
                        lastResult = null
                        heldIntent = null
                        consumed = false
                        didProcess = false
                    },
                )
            }
        }
    }
}
