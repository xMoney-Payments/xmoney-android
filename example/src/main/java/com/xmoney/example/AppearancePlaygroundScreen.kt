package com.xmoney.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.xmoney.paymentelement.ui.XCoinFlipLoader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.CardGrouping
import com.xmoney.payments.config.CardInputsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.config.UserInterfaceStyle
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment

private val playgroundStyleOptions = listOf(
    DemoOption("Automatic", "automatic"),
    DemoOption("Light", "alwaysLight"),
    DemoOption("Dark", "alwaysDark"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePlaygroundScreen(
    initialState: DemoAppearanceEditorState,
    onBack: () -> Unit,
    onApply: (DemoAppearanceEditorState) -> Unit,
) {
    val draft = remember { initialState.snapshot() }
    var pendingOrder by remember { mutableStateOf<PaymentIntent?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var spacedInputs by remember { mutableStateOf(true) }

    val configuration = remember(draft.appearanceSignature, spacedInputs) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(enabled = false),
            ),
            card = CardConfig(
                savedCards = SavedCardsConfig(enabled = false),
                inputs = CardInputsConfig(
                    grouping = if (spacedInputs) CardGrouping.SPACED else CardGrouping.CONDENSED,
                ),
            ),
            options = OptionsConfig(
                locale = "en-US",
                style = UserInterfaceStyle.from(draft.style),
                appearance = draft.toAppearanceConfig(),
            ),
        )
    }

    val embedded = rememberEmbeddedPayment(
        configuration = configuration,
        onResult = {},
    )

    LaunchedEffect(Unit) {
        if (BuildConfig.PUBLIC_KEY.isBlank() ||
            BuildConfig.PUBLIC_KEY.contains("replace", ignoreCase = true)
        ) {
            error = "Set PUBLIC_KEY in example/secrets.properties"
            return@LaunchedEffect
        }
        if (BuildConfig.API_KEY.isBlank() ||
            BuildConfig.API_KEY.contains("your_api_key", ignoreCase = true)
        ) {
            error = "Set API_KEY in example/secrets.properties"
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        try {
            pendingOrder = CheckoutApi.createPaymentIntent(
                apiBase = BuildConfig.API_BASE,
                publicKey = BuildConfig.PUBLIC_KEY,
                apiKey = BuildConfig.API_KEY,
                currency = BuildConfig.CURRENCY,
                description = BuildConfig.DESCRIPTION,
            )
        } catch (e: Exception) {
            pendingOrder = null
            error = e.message ?: "Failed to create preview order"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Customize appearance",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.background,
            ) {
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DemoColors.Accent,
                        contentColor = DemoColors.OnAccent,
                    ),
                ) {
                    Text(
                        text = "Apply to checkout",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ClassicsGallery(
                selectedPresetId = draft.selectedPresetId,
                onPresetSelected = { draft.applyPreset(it) },
                showDescriptions = true,
            )

            PreviewSection(
                isLoading = isLoading,
                error = error,
                pendingOrder = pendingOrder,
                appearanceSignature = "${draft.appearanceSignature}|inputs=${if (spacedInputs) "spaced" else "condensed"}",
                embedded = embedded,
            )

            StyleSection(
                selected = playgroundStyleOptions.firstOrNull { it.value == draft.style }
                    ?: playgroundStyleOptions.first(),
                onSelected = { draft.updateStyle(it.value) },
            )

            InputsLayoutToggle(
                spaced = spacedInputs,
                onSpacedChange = { spacedInputs = it },
            )

            ColorControlsSection(draft)

            ShapeControlsSection(draft)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PreviewSection(
    isLoading: Boolean,
    error: String?,
    pendingOrder: PaymentIntent?,
    appearanceSignature: String,
    embedded: com.xmoney.paymentelement.EmbeddedPaymentController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Live preview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Embedded element updates as you change tokens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(16.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            isLoading && pendingOrder == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    XCoinFlipLoader(
                        color = MaterialTheme.colorScheme.secondary,
                        size = 48.dp,
                    )
                    Text(
                        text = "Preparing preview…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            pendingOrder != null -> {
                key(appearanceSignature) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 2.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    Color(0x1416141A),
                                    RoundedCornerShape(20.dp),
                                )
                                .clip(RoundedCornerShape(20.dp)),
                        ) {
                            PaymentElement(
                                controller = embedded,
                                intent = pendingOrder,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputsLayoutToggle(
    spaced: Boolean,
    onSpacedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Spaced inputs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Separate labeled fields (vs condensed)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = spaced,
            onCheckedChange = onSpacedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = DemoColors.Accent,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun StyleSection(
    selected: DemoOption,
    onSelected: (DemoOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Style",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            playgroundStyleOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected.value == option.value,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = playgroundStyleOptions.size,
                    ),
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorControlsSection(draft: DemoAppearanceEditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Colors",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ColorRow("Primary", draft.primary) {
            draft.primary = it
            draft.markCustomized()
        }
        ColorRow("Background", draft.background) {
            draft.background = it
            draft.markCustomized()
        }
        ColorRow("Component bg", draft.componentBackground) {
            draft.componentBackground = it
            draft.markCustomized()
        }
        ColorRow("Border", draft.componentBorder) {
            draft.componentBorder = it
            draft.markCustomized()
        }
        ColorRow(
            label = "Container border",
            value = draft.containerBorder,
            allowNone = true,
            onValueChange = {
                draft.containerBorder = it
                draft.markCustomized()
            },
        )
        ColorRow("Primary text", draft.primaryText) {
            draft.primaryText = it
            draft.markCustomized()
        }
        ColorRow("Secondary text", draft.secondaryText) {
            draft.secondaryText = it
            draft.markCustomized()
        }
        ColorRow("Error", draft.error) {
            draft.error = it
            draft.markCustomized()
        }
        ColorRow("Button bg", draft.primaryButtonBackground) {
            draft.primaryButtonBackground = it
            draft.markCustomized()
        }
        ColorRow("Button text", draft.primaryButtonText) {
            draft.primaryButtonText = it
            draft.markCustomized()
        }
    }
}

@Composable
private fun ShapeControlsSection(draft: DemoAppearanceEditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Shapes & type",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FloatSliderRow(
            label = "Border radius",
            value = draft.borderRadius,
            valueRange = 0f..28f,
            valueLabel = "${draft.borderRadius.toInt()} dp",
            onValueChange = {
                draft.borderRadius = it
                draft.markCustomized()
            },
        )
        FloatSliderRow(
            label = "Border width",
            value = draft.borderWidth,
            valueRange = 0f..3f,
            valueLabel = String.format("%.1f dp", draft.borderWidth),
            onValueChange = {
                draft.borderWidth = it
                draft.markCustomized()
            },
        )
        FloatSliderRow(
            label = "Font scale",
            value = draft.fontScale,
            valueRange = 0.85f..1.25f,
            valueLabel = String.format("%.2f×", draft.fontScale),
            onValueChange = {
                draft.fontScale = it
                draft.markCustomized()
            },
        )
        FloatSliderRow(
            label = "Button radius",
            value = draft.primaryButtonBorderRadius,
            valueRange = 0f..32f,
            valueLabel = "${draft.primaryButtonBorderRadius.toInt()} dp",
            onValueChange = {
                draft.primaryButtonBorderRadius = it
                draft.markCustomized()
            },
        )
    }
}

@Composable
private fun ColorRow(
    label: String,
    value: String,
    allowNone: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val parsed = parseDemoHexColor(value)
    val isNone = value.equals("none", ignoreCase = true) ||
        value.equals("transparent", ignoreCase = true)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isNone -> Color.Transparent
                            else -> parsed ?: Color.Gray
                        },
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
        }
        if (allowNone) {
            TextButton(
                onClick = { onValueChange("none") },
                enabled = !isNone,
            ) {
                Text("None")
            }
        }
    }
}

@Composable
private fun FloatSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = DemoColors.Accent,
                activeTrackColor = DemoColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
