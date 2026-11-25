package com.xmoney.example.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xmoney.example.BuildConfig
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.theme.ExampleColors
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.ui.ExampleLoader
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.ExampleSwitchRow
import com.xmoney.example.ui.ExampleTopBar
import com.xmoney.example.ui.MerchantReadyGate
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.ColorSchemePreference
import com.xmoney.payments.config.Corners
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult

private val appearanceCornerOptions = listOf(
    DemoOption("Sharp", "sharp"),
    DemoOption("Default", "default"),
    DemoOption("Rounded", "rounded"),
)

@Composable
fun AppearancePlaygroundScreen(
    state: DemoAppearanceEditorState,
    onBack: () -> Unit,
) {
    val styled = state.toAppearance().copy(
        colorScheme = playgroundResolvedStyle(ColorSchemePreference.from(state.style)),
    )
    var pendingOrder by remember { mutableStateOf<PaymentIntent?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var brandText by remember { mutableStateOf(demoBrandHex(state.appearance)) }
    val configuration = remember {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(googlePay = GooglePayConfig(enabled = false)),
            card = CardConfig(savedCards = SavedCardsConfig(enabled = false)),
            options = OptionsConfig(locale = "en-US", appearance = styled),
        )
    }
    val embedded = rememberEmbeddedPayment(
        configuration = configuration,
        onResult = { if (it is PaymentResult.Failed) loadFailed = true },
    )
    LaunchedEffect(styled) { embedded.updateAppearance(styled) }
    LaunchedEffect(state.appearance.brandColor) {
        val next = demoBrandHex(state.appearance)
        if (normalizeDemoHex(brandText) != normalizeDemoHex(next)) brandText = next
    }
    LaunchedEffect(Unit) {
        DemoCheckoutBackend.secretsError()?.let {
            error = it
            return@LaunchedEffect
        }
        isLoading = true
        try {
            pendingOrder = DemoCheckoutBackend.createPaymentIntent(description = BuildConfig.DESCRIPTION)
        } catch (e: Exception) {
            error = e.message ?: "Failed to create preview order"
        } finally {
            isLoading = false
        }
    }
    val cornerValue = when (state.appearance.shape.corners) {
        Corners.Sharp -> "sharp"
        Corners.Rounded -> "rounded"
        else -> "default"
    }
    val fontOption = playgroundFontFamilyOptions.option(state.appearance.typography.fontFamily.orEmpty())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExampleTopBar(
                title = "Appearance",
                onBack = onBack,
                actions = {
                    TextButton(onClick = onBack) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Presets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            PlaygroundPresetSwatches(
                selectedPresetId = state.selectedPresetId,
                onPresetSelected = { state.applyPreset(it) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = brandText,
                onValueChange = {
                    brandText = it
                    state.updateBrand(it)
                },
                label = { Text("Brand") },
                placeholder = { Text("#RRGGBB") },
                singleLine = true,
                shape = RoundedCornerShape(ExampleRadii.inner),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExampleColors.Purple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            PlaygroundLabeledBlock(label = "Corners", modifier = Modifier.padding(horizontal = 20.dp)) {
                PlaygroundSegmentedRow(
                    options = appearanceCornerOptions,
                    selected = appearanceCornerOptions.option(cornerValue),
                    onSelected = { option ->
                        val corners = when (option.value) {
                            "sharp" -> Corners.Sharp
                            "rounded" -> Corners.Rounded
                            else -> Corners.Default
                        }
                        state.updateCorners(corners)
                    },
                )
            }
            ExampleSwitchRow(
                title = "Pill buttons",
                subtitle = "",
                checked = state.appearance.shape.pillButtons,
                onCheckedChange = { state.updatePillButtons(it) },
            )
            PlaygroundDropdown(
                label = "Font",
                options = playgroundFontFamilyOptions,
                selected = fontOption,
                onSelected = { state.updateFont(it.value) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Preview",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (error != null) {
                ExampleStatusChip(error!!, ExampleStatusKind.Error, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (isLoading && pendingOrder == null) {
                ExampleLoader(
                    message = "Preparing checkout…",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else if (pendingOrder != null) {
                MerchantReadyGate(
                    ready = !loadFailed,
                    message = "Preparing checkout…",
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    PaymentElement(controller = embedded, intent = pendingOrder!!)
                }
            }
        }
    }
}
