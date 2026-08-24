package com.xmoney.example.samples

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.BuildConfig
import com.xmoney.example.R
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.exampleAppearance
import com.xmoney.example.exampleForcedStyle
import com.xmoney.example.exampleWalletAppearance
import com.xmoney.example.theme.ExampleTheme
import com.xmoney.example.theme.ExampleThemeController
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleLoader
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.ExampleTopBar
import com.xmoney.example.ui.MerchantReadyGate
import com.xmoney.example.ui.SampleOrderCard
import com.xmoney.example.ui.TestCardsAction
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
 * Views interop: an XML layout hosts [PaymentElement] through [ComposeView].
 *
 * Give the host [ComposeView] a bounded height (`0dp` + `layout_weight`, or
 * `match_parent`) and scroll in Compose. A wrap_content ComposeView inside
 * NestedScrollView is measured with infinite height and crashes.
 *
 * Copy this pattern when the rest of checkout is still View-based.
 * Fetch [PaymentIntent] from **your** server; do not ship API keys in the app.
 */
class EmbeddedViewsSampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedded_views)

        val root = findViewById<View>(R.id.root)
        findViewById<ComposeView>(R.id.header).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                ExampleTheme {
                    val dark = ExampleThemeController.isDark(isSystemInDarkTheme())
                    SideEffect {
                        root.setBackgroundColor(
                            if (dark) {
                                Color.argb(255, 9, 9, 11)
                            } else {
                                Color.argb(255, 244, 243, 251)
                            },
                        )
                    }
                    ExampleTopBar(
                        title = "Embedded in Views",
                        subtitle = "XML layout hosting PaymentElement inside a ComposeView.",
                        onBack = { finish() },
                        actions = { TestCardsAction() },
                    )
                }
            }
        }

        findViewById<ComposeView>(R.id.payment_element).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                ExampleTheme {
                    ViewsEmbeddedContent()
                }
            }
        }
    }
}

@Composable
private fun ViewsEmbeddedContent() {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        modifier = Modifier.fillMaxWidth(),
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
