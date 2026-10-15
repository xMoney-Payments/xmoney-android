package com.xmoney.example.samples

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
import com.xmoney.payments.config.SubmitButtonConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import kotlinx.coroutines.launch

/**
 * Merchant-owned Pay button. The SDK form stays visible; [SubmitButtonConfig.visible]
 * is false and the app calls [com.xmoney.paymentelement.EmbeddedPaymentController.confirm].
 */
class MerchantPayButtonSampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                MerchantPayButtonScreen(activity = this)
            }
        }
    }
}

@Composable
private fun MerchantPayButtonScreen(activity: FragmentActivity) {
    var intent by remember { mutableStateOf<PaymentIntent?>(null) }
    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
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
                submitButton = SubmitButtonConfig(visible = false),
            ),
            options = OptionsConfig(
                style = style,
                appearance = appearance,
            ),
        )
    }

    val embedded = rememberEmbeddedPayment(
        configuration = configuration,
        onResult = { result ->
            lastResult = result
            // A failed first load emits no Ready: open the gate onto the SDK failure text.
            if (result is PaymentResult.Failed) {
                ready = true
                loadFailed = true
            }
        },
    )
    LaunchedEffect(appearance) { embedded.updateAppearance(appearance) }
    LaunchedEffect(style) { embedded.updateStyle(style) }
    LaunchedEffect(wallet) { embedded.updateWalletAppearance(wallet) }

    LaunchedEffect(lastResult, embedded.isOrderConsumed) {
        if (lastResult is PaymentResult.Canceled && !embedded.isOrderConsumed) {
            lastResult = null
        }
    }

    fun loadOrder(retry: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            lastResult = null
            if (!retry) {
                ready = false
                loadFailed = false
            }
            processing = false
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
        title = "Merchant Pay button",
        subtitle = "SDK form, your CTA. confirm() after Ready.",
        activity = activity,
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
                MerchantReadyGate(
                    ready = ready,
                    message = "Preparing checkout…",
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    PaymentElement(
                        controller = embedded,
                        intent = intent!!,
                        modifier = Modifier.fillMaxWidth(),
                        onEvent = { event ->
                            when (event) {
                                EmbeddedEvent.Ready -> {
                                    ready = true
                                    loadFailed = false
                                }
                                is EmbeddedEvent.Processing -> processing = event.isProcessing
                                is EmbeddedEvent.UpdateFailed -> Unit
                            }
                        },
                    )
                }
                if (loadFailed) {
                    // Keep the gate open: a repeat failure in the same streak does not call onResult again.
                    ExampleButton(
                        label = "Try again",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = { loadOrder(retry = true) },
                    )
                } else if (ready) {
                    ExampleButton(
                        label = "Pay",
                        loading = processing,
                        enabled = embedded.isInteractionEnabled,
                        onClick = { embedded.confirm() },
                    )
                }
            }
        }
        error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
    }
}
