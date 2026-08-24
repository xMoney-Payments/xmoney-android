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
import com.xmoney.googlepay.GooglePay
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import kotlinx.coroutines.launch

/**
 * Imperative Google Pay — [GooglePay.present] from a FragmentActivity.
 *
 * Fetch [PaymentIntent] from **your** server. Pre-pay wallet dismiss delivers
 * [PaymentResult.Canceled] and does not consume; present the same intent again.
 */
class GooglePayActivitySample : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                val style = exampleForcedStyle()
                val wallet = exampleWalletAppearance()
                val configuration = remember(style, wallet.color) {
                    PaymentConfig(
                        publicKey = BuildConfig.PUBLIC_KEY,
                        paymentMethods = PaymentMethodsConfig(
                            googlePay = GooglePayConfig(enabled = true, appearance = wallet),
                        ),
                        card = CardConfig(
                            savedCards = SavedCardsConfig(enabled = false),
                        ),
                        options = OptionsConfig(
                            style = style,
                            appearance = exampleAppearance(),
                        ),
                    )
                }
                val googlePay = remember(configuration.options.style) {
                    GooglePay(configuration)
                }
                GooglePayActivityScreen(activity = this, googlePay = googlePay)
            }
        }
    }
}

@Composable
private fun GooglePayActivityScreen(
    activity: GooglePayActivitySample,
    googlePay: GooglePay,
) {
    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var heldIntent by remember { mutableStateOf<PaymentIntent?>(null) }
    var didProcess by remember { mutableStateOf(false) }
    var consumed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun present(intent: PaymentIntent) {
        didProcess = false
        heldIntent = intent
        googlePay.present(
            activity,
            intent,
            onEvent = { event ->
                when (event) {
                    GooglePayEvent.Ready -> loading = false
                    is GooglePayEvent.Processing -> {
                        if (event.isProcessing) didProcess = true
                    }
                }
            },
        ) { result ->
            lastResult = result
            loading = false
            consumed = orderConsumed(result, didProcess)
            if (consumed) heldIntent = null
        }
    }

    SampleScaffold(
        title = "Google Pay (Activity)",
        subtitle = "GooglePay(config).present(activity, intent).",
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
                ExampleStatusChip("You closed Google Pay before finishing.", ExampleStatusKind.Neutral)
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
