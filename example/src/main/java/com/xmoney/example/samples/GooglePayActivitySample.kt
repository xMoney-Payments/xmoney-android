package com.xmoney.example.samples

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.BuildConfig
import com.xmoney.example.SAMPLE_AMOUNT_MINOR
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.exampleAppearance
import com.xmoney.example.exampleForcedStyle
import com.xmoney.example.exampleWalletAppearance
import com.xmoney.example.formatMoney
import com.xmoney.example.orderConsumed
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.theme.ExampleTheme
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleCard
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AMOUNT_STEP_MINOR = 500L
private const val AMOUNT_MIN_MINOR = 500L

/**
 * Imperative Google Pay — [GooglePay.present] from a FragmentActivity.
 *
 * Fetch [PaymentIntent] from **your** server. Pre-pay wallet dismiss delivers
 * [PaymentResult.Canceled] and does not consume; present the same intent again.
 * While the overlay is open, [GooglePay.updateOrder] rebinds a new intent
 * without dismissing the host.
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
    var hostOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var amountMinor by remember { mutableLongStateOf(SAMPLE_AMOUNT_MINOR) }
    val scope = rememberCoroutineScope()

    fun present(intent: PaymentIntent) {
        didProcess = false
        heldIntent = intent
        hostOpen = true
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
            hostOpen = false
            consumed = orderConsumed(result, didProcess)
            if (consumed) heldIntent = null
        }
    }

    LaunchedEffect(amountMinor) {
        if (!hostOpen || consumed) return@LaunchedEffect
        delay(300)
        if (!hostOpen || consumed) return@LaunchedEffect
        try {
            val next = DemoCheckoutBackend.createPaymentIntent(amountMinor = amountMinor)
            googlePay.updateOrder(next)
            heldIntent = next
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not update order"
        }
    }

    SampleScaffold(
        title = "Google Pay (Activity)",
        subtitle = "GooglePay(config).present() / updateOrder().",
        activity = activity,
        showTestCards = true,
    ) {
        if (!consumed) {
            ActivityOrderCard(
                amountMinor = amountMinor,
                enabled = !loading,
                onAmountChange = { next ->
                    amountMinor = next
                    if (!hostOpen) heldIntent = null
                },
            )
            ExampleButton(
                label = if (lastResult is PaymentResult.Canceled) "Continue" else "Pay",
                loading = loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        lastResult = null
                        try {
                            val intent = heldIntent
                                ?: DemoCheckoutBackend.createPaymentIntent(amountMinor = amountMinor)
                            present(intent)
                        } catch (e: Exception) {
                            error = e.message ?: "Could not create order"
                            loading = false
                            hostOpen = false
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
                        hostOpen = false
                        amountMinor = SAMPLE_AMOUNT_MINOR
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivityOrderCard(
    amountMinor: Long,
    enabled: Boolean,
    onAmountChange: (Long) -> Unit,
) {
    ExampleCard {
        Text(
            text = "ORDER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = BuildConfig.DESCRIPTION.ifBlank { "Checkout item" },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatMoney(amountMinor, BuildConfig.CURRENCY),
                style = MaterialTheme.typography.headlineMedium,
            )
            AmountStepper(
                amountMinor = amountMinor,
                enabled = enabled,
                onAmountChange = onAmountChange,
            )
        }
    }
}

@Composable
private fun AmountStepper(
    amountMinor: Long,
    enabled: Boolean,
    onAmountChange: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(ExampleRadii.pill)
    Row(
        modifier = Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onAmountChange((amountMinor - AMOUNT_STEP_MINOR).coerceAtLeast(AMOUNT_MIN_MINOR)) },
            enabled = enabled && amountMinor > AMOUNT_MIN_MINOR,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease amount", modifier = Modifier.size(16.dp))
        }
        Text(
            text = formatMoney(AMOUNT_STEP_MINOR, BuildConfig.CURRENCY),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(
            onClick = { onAmountChange(amountMinor + AMOUNT_STEP_MINOR) },
            enabled = enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase amount", modifier = Modifier.size(16.dp))
        }
    }
}
