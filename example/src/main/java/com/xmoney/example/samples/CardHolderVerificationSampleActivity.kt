package com.xmoney.example.samples

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.xmoney.example.orderConsumed
import com.xmoney.example.theme.ExampleTheme
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleCard
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.SampleScaffold
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.CardHolderName
import com.xmoney.payments.config.CardHolderVerification
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.model.CardHolderMatchStatus
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.launch

/**
 * Card holder verification — optional pre-pay name check.
 *
 * Requires the site to have name-check validation enabled. The callback runs
 * after account-validation; return `true` to continue pay, `false` to block.
 *
 * Fetch [PaymentIntent] from **your** server. Do not ship API keys in the app.
 */
class CardHolderVerificationSampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                CardHolderSampleScreen(activity = this)
            }
        }
    }
}

@Composable
private fun CardHolderSampleScreen(activity: FragmentActivity) {
    var lastMatch by remember { mutableStateOf<String?>(null) }
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
                cardHolderVerification = CardHolderVerification(
                    name = CardHolderName(firstName = "John", lastName = "Doe"),
                    onCardHolderVerification = { result ->
                        lastMatch = result.status.raw
                        result.status == CardHolderMatchStatus.MATCHED
                    },
                ),
            ),
            options = OptionsConfig(style = style, appearance = exampleAppearance()),
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
        title = "Name check",
        subtitle = "Pays only when the cardholder name matches John Doe.",
        activity = activity,
        showTestCards = true,
        nameCheckHint = true,
    ) {
        ExampleCard {
            Text(
                text = "Expected name",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "John Doe",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Use a test card whose account-validation result matches that name, or the SDK will block pay.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!consumed) {
            ExampleButton(
                label = if (lastResult is PaymentResult.Canceled) "Continue" else "Pay",
                loading = loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        lastResult = null
                        lastMatch = null
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
        lastMatch?.let {
            ExampleStatusChip("Verification: $it", ExampleStatusKind.Neutral)
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
                        lastMatch = null
                        heldIntent = null
                        consumed = false
                        didProcess = false
                    },
                )
            }
        }
    }
}
