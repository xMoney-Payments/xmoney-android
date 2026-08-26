package com.xmoney.example.samples

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.BuildConfig
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.exampleAppearance
import com.xmoney.example.exampleForcedStyle
import com.xmoney.example.exampleWalletAppearance
import com.xmoney.example.theme.ExampleTheme
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleLoader
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.MerchantReadyGate
import com.xmoney.example.ui.SampleOrderCard
import com.xmoney.example.ui.SampleScaffold
import com.xmoney.googlepay.GooglePayButton
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.googlepay.rememberGooglePay
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
 * Minimal standalone Google Pay integration.
 *
 * Copy this file as a starting point. Fetch [PaymentIntent] from **your** server.
 * After a terminal consumed result bind a new intent. Pre-auth cancel (user
 * dismisses the wallet sheet) delivers [PaymentResult.Canceled] and does not consume.
 */
class GooglePaySampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                GooglePaySampleScreen(activity = this)
            }
        }
    }
}

@Composable
private fun GooglePaySampleScreen(activity: FragmentActivity) {
    var intent by remember { mutableStateOf<PaymentIntent?>(null) }
    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var bound by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val style = exampleForcedStyle()
    val wallet = exampleWalletAppearance()
    val appearance = exampleAppearance()
    val configuration = remember {
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
                appearance = appearance,
            ),
        )
    }

    val googlePay = rememberGooglePay(
        configuration = configuration,
        onResult = { lastResult = it },
    )
    LaunchedEffect(wallet) { googlePay.updateAppearance(wallet) }

    fun loadOrder() {
        scope.launch {
            loading = true
            error = null
            lastResult = null
            bound = false
            intent = null
            try {
                intent = DemoCheckoutBackend.createPaymentIntent()
            } catch (e: Exception) {
                error = e.message ?: "Could not create order"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadOrder() }

    val consumed = googlePay.isOrderConsumed

    SampleScaffold(
        title = "Google Pay",
        subtitle = "Standalone wallet button in your screen.",
        activity = activity,
        scrollable = false,
        showTestCards = true,
    ) {
        SampleOrderCard()
        when {
            consumed -> {
                ExampleResultPanel(lastResult!!)
                ExampleButton(
                    label = "New payment",
                    variant = ExampleButtonVariant.Secondary,
                    onClick = { loadOrder() },
                )
            }
            loading && intent == null -> ExampleLoader(message = "Preparing Google Pay…")
            intent != null -> {
                MerchantReadyGate(ready = bound, message = "Preparing Google Pay…") {
                    GooglePayButton(
                        controller = googlePay,
                        intent = intent!!,
                        modifier = Modifier.fillMaxWidth(),
                        onEvent = { event ->
                            if (event is GooglePayEvent.Ready) bound = true
                        },
                    )
                }
                if (lastResult is PaymentResult.Canceled && !consumed) {
                    ExampleStatusChip(
                        "You closed Google Pay before finishing.",
                        ExampleStatusKind.Neutral,
                    )
                }
                if (bound && !googlePay.isReady) {
                    ExampleStatusChip(
                        "Google Pay isn’t available on this device.",
                        ExampleStatusKind.Neutral,
                    )
                    ExampleButton(
                        label = "Pay with card",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = {
                            context.startActivity(
                                Intent(context, EmbeddedPaymentSampleActivity::class.java),
                            )
                        },
                    )
                }
            }
        }
        error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
    }
}
