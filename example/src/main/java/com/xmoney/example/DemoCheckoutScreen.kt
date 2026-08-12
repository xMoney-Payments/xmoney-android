package com.xmoney.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.config.SubmitButtonConfig
import com.xmoney.payments.config.SubmitButtonType
import com.xmoney.payments.config.UserInterfaceStyle
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.CardGrouping
import com.xmoney.payments.config.CardHolderName
import com.xmoney.payments.config.CardHolderVerification
import com.xmoney.payments.config.CardInputsConfig
import com.xmoney.payments.model.CardHolderMatchStatus
import com.xmoney.paymentelement.EmbeddedPaymentController
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment
import com.xmoney.paymentelement.ui.XCoinButtonMark
import com.xmoney.paymentelement.ui.XCoinFlipLoader
import com.xmoney.googlepay.GooglePayButton
import com.xmoney.googlepay.GooglePayController
import com.xmoney.googlepay.rememberGooglePay
import com.xmoney.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.launch

internal enum class IntegrationMode(val label: String) {
    PaymentSheet("Sheet"),
    GooglePay("Google Pay"),
    Embedded("Embedded"),
}

internal data class DemoOption(val label: String, val value: String)

private val localeOptions = listOf(
    DemoOption("English", "en-US"),
    DemoOption("Greek", "el-GR"),
    DemoOption("Romanian", "ro-RO"),
)

private val themeOptions = listOf(
    DemoOption("Automatic", "automatic"),
    DemoOption("Light", "alwaysLight"),
    DemoOption("Dark", "alwaysDark"),
)

private val buttonTypeOptions = listOf(
    DemoOption("Pay", "pay"),
    DemoOption("Book", "book"),
    DemoOption("Buy", "buy"),
    DemoOption("Checkout", "checkout"),
    DemoOption("Donate", "donate"),
    DemoOption("Order", "order"),
    DemoOption("Subscribe", "subscribe"),
    DemoOption("Top up", "topUp"),
    DemoOption("Deposit", "deposit"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoCheckoutScreen(
    appearanceState: DemoAppearanceEditorState,
    onCustomizeAppearance: () -> Unit,
) {
    var selectedLocale by remember { mutableStateOf(localeOptions.first()) }
    var selectedButtonType by remember { mutableStateOf(buttonTypeOptions.first()) }
    var googlePayEnabled by remember { mutableStateOf(true) }
    var savedCardsEnabled by remember { mutableStateOf(true) }
    var saveCardOptInVisible by remember { mutableStateOf(true) }
    var spacedInputs by remember { mutableStateOf(false) }
    var cardHolderVerificationEnabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(IntegrationMode.PaymentSheet) }
    var settingsExpanded by remember { mutableStateOf(false) }

    var lastResult by remember { mutableStateOf<String?>(null) }
    var inlineOrderConsumed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingOrder by remember { mutableStateOf<PaymentIntent?>(null) }
    val scope = rememberCoroutineScope()

    val selectedTheme = themeOptions.firstOrNull { it.value == appearanceState.style }
        ?: themeOptions.first()

    val configuration = remember(
        selectedLocale,
        appearanceState.appearanceSignature,
        selectedButtonType,
        googlePayEnabled,
        savedCardsEnabled,
        saveCardOptInVisible,
        spacedInputs,
        cardHolderVerificationEnabled,
    ) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(enabled = googlePayEnabled),
            ),
            card = CardConfig(
                savedCards = SavedCardsConfig(
                    enabled = savedCardsEnabled,
                    optInVisible = saveCardOptInVisible,
                ),
                cardHolderVerification = if (cardHolderVerificationEnabled) {
                    CardHolderVerification(
                        name = CardHolderName(
                            firstName = "John",
                            middleName = "",
                            lastName = "Doe",
                        ),
                        onCardHolderVerification = { result ->
                            result.status == CardHolderMatchStatus.MATCHED
                        },
                    )
                } else {
                    null
                },
                inputs = CardInputsConfig(
                    grouping = if (spacedInputs) CardGrouping.SPACED else CardGrouping.CONDENSED,
                ),
                submitButton = SubmitButtonConfig(
                    type = SubmitButtonType.from(selectedButtonType.value),
                ),
            ),
            options = OptionsConfig(
                locale = selectedLocale.value,
                style = UserInterfaceStyle.from(appearanceState.style),
                appearance = appearanceState.toAppearanceConfig(),
            ),
        )
    }

    fun formatResult(result: PaymentResult): String = when (result.status) {
        PaymentResult.Status.COMPLETE -> "Payment complete"
        PaymentResult.Status.FAILED -> "Failed: ${result.errorMessage}"
        PaymentResult.Status.CANCELED -> "Canceled"
    }

    fun handleInlineResult(result: PaymentResult) {
        lastResult = formatResult(result)
        when (result.status) {
            PaymentResult.Status.COMPLETE,
            PaymentResult.Status.FAILED,
            PaymentResult.Status.CANCELED,
            -> {
                pendingOrder = null
                inlineOrderConsumed = true
            }
        }
    }

    val checkout = rememberPaymentSheet(
        configuration = configuration,
        onResult = { lastResult = formatResult(it) },
    )
    val googlePay = rememberGooglePay(
        configuration = configuration,
        onResult = { handleInlineResult(it) },
    )
    val embedded = rememberEmbeddedPayment(
        configuration = configuration,
        onResult = { handleInlineResult(it) },
    )

    suspend fun createOrder(): PaymentIntent {
        return CheckoutApi.createPaymentIntent(
            apiBase = BuildConfig.API_BASE,
            publicKey = BuildConfig.PUBLIC_KEY,
            apiKey = BuildConfig.API_KEY,
            currency = BuildConfig.CURRENCY,
            description = BuildConfig.DESCRIPTION,
        )
    }

    fun validateSecrets(): String? {
        if (BuildConfig.PUBLIC_KEY.isBlank() ||
            BuildConfig.PUBLIC_KEY.contains("replace", ignoreCase = true)
        ) {
            return "Set PUBLIC_KEY in example/secrets.properties"
        }
        if (BuildConfig.API_KEY.isBlank() ||
            BuildConfig.API_KEY.contains("your_api_key", ignoreCase = true)
        ) {
            return "Set API_KEY in example/secrets.properties"
        }
        return null
    }

    fun prepareInlineSurface() {
        validateSecrets()?.let {
            error = it
            pendingOrder = null
            return
        }
        scope.launch {
            isLoading = true
            error = null
            lastResult = null
            inlineOrderConsumed = false
            try {
                pendingOrder = createOrder()
            } catch (e: Exception) {
                pendingOrder = null
                error = e.message ?: "Failed to create order"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(
        mode,
        googlePayEnabled,
        savedCardsEnabled,
        saveCardOptInVisible,
        spacedInputs,
        configuration,
    ) {
        pendingOrder = null
        error = null
        lastResult = null
        inlineOrderConsumed = false
        if (mode != IntegrationMode.GooglePay && mode != IntegrationMode.Embedded) {
            return@LaunchedEffect
        }
        val secretsError = validateSecrets()
        if (secretsError != null) {
            error = secretsError
            return@LaunchedEffect
        }
        isLoading = true
        try {
            pendingOrder = createOrder()
        } catch (e: Exception) {
            pendingOrder = null
            error = e.message ?: "Failed to create order"
        } finally {
            isLoading = false
        }
    }

    val payLabel = remember(selectedButtonType) {
        when (selectedButtonType.value) {
            "book" -> "Book with xMoney"
            "buy" -> "Buy with xMoney"
            "checkout" -> "Checkout with xMoney"
            "donate" -> "Donate with xMoney"
            "order" -> "Order with xMoney"
            "subscribe" -> "Subscribe with xMoney"
            "topUp" -> "Top up with xMoney"
            "deposit" -> "Deposit with xMoney"
            else -> "Pay with xMoney"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DemoHeader()

            OrderSummary(
                currency = BuildConfig.CURRENCY,
                description = BuildConfig.DESCRIPTION,
            )

            SettingsSection(
                expanded = settingsExpanded,
                onToggle = { settingsExpanded = !settingsExpanded },
                selectedLocale = selectedLocale,
                selectedTheme = selectedTheme,
                selectedButtonType = selectedButtonType,
                googlePayEnabled = googlePayEnabled,
                savedCardsEnabled = savedCardsEnabled,
                saveCardOptInVisible = saveCardOptInVisible,
                spacedInputs = spacedInputs,
                cardHolderVerificationEnabled = cardHolderVerificationEnabled,
                selectedPresetId = appearanceState.selectedPresetId,
                onLocale = { selectedLocale = it },
                onTheme = { appearanceState.updateStyle(it.value) },
                onButtonType = { selectedButtonType = it },
                onGooglePayEnabled = { googlePayEnabled = it },
                onSavedCardsEnabled = { savedCardsEnabled = it },
                onSaveCardOptInVisible = { saveCardOptInVisible = it },
                onSpacedInputs = { spacedInputs = it },
                onCardHolderVerificationEnabled = { cardHolderVerificationEnabled = it },
                onPresetSelected = { appearanceState.applyPreset(it) },
                onCustomizeAppearance = onCustomizeAppearance,
            )

            PaymentSection(
                mode = mode,
                onModeChange = { mode = it },
                payLabel = payLabel,
                isLoading = isLoading,
                pendingOrder = pendingOrder,
                hasTerminalResult = inlineOrderConsumed,
                googlePay = googlePay,
                embedded = embedded,
                onPaySheet = {
                    validateSecrets()?.let { error = it; return@PaymentSection }
                    scope.launch {
                        isLoading = true
                        error = null
                        lastResult = null
                        try {
                            checkout.present(createOrder())
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to open payment sheet"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                onRetryPrepare = { prepareInlineSurface() },
            )

            StatusFooter(error = error, lastResult = lastResult)
        }
    }
}

@Composable
private fun DemoHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "xMoney",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Demo checkout",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrderSummary(currency: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ORDER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = description.ifBlank { "Checkout item" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Demo order · live API",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = sampleAmountLabel(currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun sampleAmountLabel(currency: String): String = when (currency.uppercase()) {
    "EUR" -> "€19.99"
    "USD" -> "$19.99"
    "GBP" -> "£19.99"
    "RON" -> "19.99 RON"
    else -> "19.99 $currency"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    selectedLocale: DemoOption,
    selectedTheme: DemoOption,
    selectedButtonType: DemoOption,
    googlePayEnabled: Boolean,
    savedCardsEnabled: Boolean,
    saveCardOptInVisible: Boolean,
    spacedInputs: Boolean,
    cardHolderVerificationEnabled: Boolean,
    selectedPresetId: String?,
    onLocale: (DemoOption) -> Unit,
    onTheme: (DemoOption) -> Unit,
    onButtonType: (DemoOption) -> Unit,
    onGooglePayEnabled: (Boolean) -> Unit,
    onSavedCardsEnabled: (Boolean) -> Unit,
    onSaveCardOptInVisible: (Boolean) -> Unit,
    onSpacedInputs: (Boolean) -> Unit,
    onCardHolderVerificationEnabled: (Boolean) -> Unit,
    onPresetSelected: (DemoAppearancePreset) -> Unit,
    onCustomizeAppearance: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Options",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide" else "Show")
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClassicsGallery(
                    selectedPresetId = selectedPresetId,
                    onPresetSelected = onPresetSelected,
                    onCustomize = onCustomizeAppearance,
                )
                DemoDropdown("Language", localeOptions, selectedLocale, onLocale)
                DemoDropdown("Appearance", themeOptions, selectedTheme, onTheme)
                DemoDropdown("Submit button", buttonTypeOptions, selectedButtonType, onButtonType)
                DemoToggle(
                    title = "Spaced inputs",
                    subtitle = "Separate labeled fields (vs condensed)",
                    checked = spacedInputs,
                    onCheckedChange = onSpacedInputs,
                )
                DemoToggle(
                    title = "Google Pay",
                    subtitle = "Show wallet button when available",
                    checked = googlePayEnabled,
                    onCheckedChange = onGooglePayEnabled,
                )
                DemoToggle(
                    title = "Saved cards",
                    subtitle = "Load and offer stored cards",
                    checked = savedCardsEnabled,
                    onCheckedChange = onSavedCardsEnabled,
                )
                DemoToggle(
                    title = "Save card opt-in",
                    subtitle = "Checkbox to save a new card",
                    checked = saveCardOptInVisible,
                    onCheckedChange = onSaveCardOptInVisible,
                )
                DemoToggle(
                    title = "Card holder verification",
                    subtitle = "Validate name via account-validation before pay",
                    checked = cardHolderVerificationEnabled,
                    onCheckedChange = onCardHolderVerificationEnabled,
                )
            }
        }
    }
}

@Composable
private fun DemoToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = DemoColors.Accent,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun PaymentSection(
    mode: IntegrationMode,
    onModeChange: (IntegrationMode) -> Unit,
    payLabel: String,
    isLoading: Boolean,
    pendingOrder: PaymentIntent?,
    hasTerminalResult: Boolean,
    googlePay: GooglePayController,
    embedded: EmbeddedPaymentController,
    onPaySheet: () -> Unit,
    onRetryPrepare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Payment",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            IntegrationMode.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = mode == item,
                    onClick = { onModeChange(item) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = IntegrationMode.entries.size,
                    ),
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        Text(
            text = modeCaption(mode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "payment-surface",
        ) { current ->
            when (current) {
                IntegrationMode.PaymentSheet -> {
                    PayCtaButton(
                        label = payLabel,
                        loading = isLoading,
                        onClick = onPaySheet,
                    )
                }

                IntegrationMode.GooglePay -> {
                    InlineMethodSurface(
                        isLoading = isLoading,
                        pendingOrder = pendingOrder,
                        emptyHint = "Preparing Google Pay…",
                        hasTerminalResult = hasTerminalResult,
                        onRetry = onRetryPrepare,
                    ) { order ->
                        GooglePayButton(
                            controller = googlePay,
                            intent = order,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                IntegrationMode.Embedded -> {
                    InlineMethodSurface(
                        isLoading = isLoading,
                        pendingOrder = pendingOrder,
                        emptyHint = "Preparing embedded element…",
                        hasTerminalResult = hasTerminalResult,
                        onRetry = onRetryPrepare,
                    ) { order ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
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
                                        RoundedCornerShape(24.dp),
                                    )
                                    .clip(RoundedCornerShape(24.dp)),
                            ) {
                                PaymentElement(
                                    controller = embedded,
                                    intent = order,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMethodSurface(
    isLoading: Boolean,
    pendingOrder: PaymentIntent?,
    emptyHint: String,
    hasTerminalResult: Boolean,
    onRetry: () -> Unit,
    content: @Composable (PaymentIntent) -> Unit,
) {
    when {
        isLoading && pendingOrder == null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                XCoinFlipLoader(
                    color = MaterialTheme.colorScheme.secondary,
                    size = 48.dp,
                )
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        pendingOrder != null -> content(pendingOrder)

        else -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (hasTerminalResult) {
                        "Order finished. Start a new payment to try again."
                    } else {
                        "Couldn’t prepare this method."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) {
                    Text(if (hasTerminalResult) "New payment" else "Try again")
                }
            }
        }
    }
}

@Composable
private fun PayCtaButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DemoColors.Accent,
            contentColor = DemoColors.OnAccent,
            disabledContainerColor = DemoColors.Accent.copy(alpha = 0.55f),
            disabledContentColor = DemoColors.OnAccent,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        if (loading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XCoinButtonMark(color = DemoColors.OnAccent)
                Text(
                    text = "Processing...",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun StatusFooter(error: String?, lastResult: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        error?.let { message ->
            StatusBanner(
                text = message,
                container = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                content = MaterialTheme.colorScheme.error,
            )
        }
        lastResult?.let { status ->
            StatusBanner(
                text = status,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StatusBanner(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

private fun modeCaption(mode: IntegrationMode): String = when (mode) {
    IntegrationMode.PaymentSheet ->
        "SDK owns the full checkout UI. Tap Pay to open the payment sheet."
    IntegrationMode.GooglePay ->
        "Standalone Google Pay button embedded in your screen."
    IntegrationMode.Embedded ->
        "Payment Element in your layout. Toggle methods under Options."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoDropdown(
    label: String,
    options: List<DemoOption>,
    selected: DemoOption,
    onSelected: (DemoOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
