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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.theme.ExampleTheme
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleCard
import com.xmoney.example.ui.ExampleLoader
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.MerchantReadyGate
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val AMOUNT_STEP_MINOR = 500L
private const val AMOUNT_MIN_MINOR = 500L

/**
 * Update the bound order without unmounting Payment Element.
 *
 * Pass the server call that creates the new `payload` + `checksum` to
 * [EmbeddedPaymentController.updateOrder]. Pay is locked during the debounce,
 * server call, and bind. Then pass the returned [PaymentIntent] to
 * [PaymentElement]; its repeated bind is a no-op.
 */
class UpdateOrderSampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                UpdateOrderSampleScreen(activity = this)
            }
        }
    }
}

@Composable
private fun UpdateOrderSampleScreen(activity: FragmentActivity) {
    var amountMinor by remember { mutableLongStateOf(SAMPLE_AMOUNT_MINOR) }
    var hostIntent by remember { mutableStateOf<PaymentIntent?>(null) }
    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

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

    val consumed = lastResult != null && embedded.isOrderConsumed

    LaunchedEffect(amountMinor, consumed, retryKey) {
        if (consumed) return@LaunchedEffect
        val initial = hostIntent == null
        if (initial) loading = true
        error = null
        try {
            if (initial) {
                hostIntent = DemoCheckoutBackend.createPaymentIntent(amountMinor = amountMinor)
            } else {
                // Pay / confirm / Google Pay are locked from here, including the
                // debounce and the server call, until the new order is bound.
                // Keep the Element mounted — do not clear intent or flip Ready.
                hostIntent = embedded.updateOrder {
                    delay(300)
                    DemoCheckoutBackend.createPaymentIntent(amountMinor = amountMinor)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not create order"
        } finally {
            if (initial) loading = false
        }
    }

    SampleScaffold(
        title = "Update order",
        subtitle = "updateOrder() a new PaymentIntent — Pay locked until it returns.",
        activity = activity,
        showTestCards = true,
    ) {
        UpdateOrderCard(
            amountMinor = amountMinor,
            enabled = !consumed && !embedded.isProcessing,
            onAmountChange = { amountMinor = it },
        )
        when {
            consumed -> {
                ExampleResultPanel(lastResult!!)
                ExampleButton(
                    label = "New payment",
                    variant = ExampleButtonVariant.Secondary,
                    onClick = {
                        lastResult = null
                        hostIntent = null
                        ready = false
                        loadFailed = false
                        amountMinor = SAMPLE_AMOUNT_MINOR
                    },
                )
            }
            loading && hostIntent == null -> ExampleLoader(message = "Preparing checkout…")
            hostIntent != null -> {
                MerchantReadyGate(ready = ready, message = "Preparing checkout…") {
                    PaymentElement(
                        controller = embedded,
                        intent = hostIntent!!,
                        modifier = Modifier.fillMaxWidth(),
                        onEvent = { event ->
                            when (event) {
                                EmbeddedEvent.Ready -> {
                                    ready = true
                                    loadFailed = false
                                }
                                is EmbeddedEvent.UpdateFailed ->
                                    error = "Could not update the order. Pay is locked until it binds."
                                is EmbeddedEvent.Processing -> Unit
                            }
                        },
                    )
                }
                if (loadFailed) {
                    // Keep the gate open: a repeat failure in the same streak does not call onResult again.
                    ExampleButton(
                        label = "Try again",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = {
                            lastResult = null
                            hostIntent = null
                            retryKey++
                        },
                    )
                }
            }
        }
        error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
    }
}

@Composable
private fun UpdateOrderCard(
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
