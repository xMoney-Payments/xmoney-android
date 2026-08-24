package com.xmoney.example.samples

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.xmoney.paymentelement.EmbeddedEvent
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment
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
 * Minimal Embedded Payment Element integration.
 *
 * Copy this file as a starting point. Fetch [PaymentIntent] from **your** server
 * (`payload` + `checksum`). The app should hold only `publicKey`.
 *
 * After a consumed terminal result hide the element and bind a **new** intent.
 * Pre-pay cancel does not consume — the element stays mounted.
 */
class EmbeddedPaymentSampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                EmbeddedPaymentSampleScreen(activity = this)
            }
        }
    }
}

@Composable
private fun EmbeddedPaymentSampleScreen(activity: FragmentActivity) {
    var intent by remember { mutableStateOf<PaymentIntent?>(null) }
    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                savedCards = SavedCardsConfig(enabled = true),
            ),
            options = OptionsConfig(
                style = style,
                appearance = appearance,
            ),
        )
    }

    val embedded = rememberEmbeddedPayment(
        configuration = configuration,
        onResult = { lastResult = it },
    )
    LaunchedEffect(appearance) { embedded.updateAppearance(appearance) }
    LaunchedEffect(style) { embedded.updateStyle(style) }
    LaunchedEffect(wallet) { embedded.updateWalletAppearance(wallet) }

    LaunchedEffect(lastResult, embedded.isOrderConsumed) {
        if (lastResult is PaymentResult.Canceled && !embedded.isOrderConsumed) {
            lastResult = null
        }
    }

    fun loadOrder() {
        scope.launch {
            loading = true
            error = null
            lastResult = null
            ready = false
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

    val consumed = lastResult != null && embedded.isOrderConsumed

    SampleScaffold(
        title = "Embedded Element",
        subtitle = "Payment form lives in your layout.",
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
            loading && intent == null -> ExampleLoader(message = "Preparing checkout…")
            intent != null -> {
                MerchantReadyGate(ready = ready, message = "Preparing checkout…") {
                    PaymentElement(
                        controller = embedded,
                        intent = intent!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        onEvent = { event ->
                            if (event is EmbeddedEvent.Ready) ready = true
                        },
                    )
                }
            }
        }
        error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
    }
}
