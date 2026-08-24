package com.xmoney.example.playground

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xmoney.example.BuildConfig
import com.xmoney.example.SAMPLE_AMOUNT_MINOR
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.formatMoney
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleLoader
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.ExampleSwitchRow
import com.xmoney.example.ui.ExampleTopBar
import com.xmoney.example.ui.MerchantReadyGate
import com.xmoney.googlepay.GooglePayButton
import com.xmoney.googlepay.GooglePayController
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.googlepay.rememberGooglePay
import com.xmoney.paymentelement.EmbeddedEvent
import com.xmoney.paymentelement.EmbeddedPaymentController
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.CardGrouping
import com.xmoney.payments.config.CardHolderName
import com.xmoney.payments.config.CardHolderVerification
import com.xmoney.payments.config.CardInputsConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.config.SubmitButtonConfig
import com.xmoney.payments.config.SubmitButtonType
import com.xmoney.payments.config.UserInterfaceStyle
import com.xmoney.payments.config.ValidationMode
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.config.WalletButtonColor
import com.xmoney.payments.config.WalletButtonType
import com.xmoney.payments.model.CardHolderMatchStatus
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AMOUNT_STEP_MINOR = 500L
private const val AMOUNT_MIN_MINOR = 500L

@Composable
fun DemoCheckoutScreen(
    appearanceState: DemoAppearanceEditorState,
    onBack: () -> Unit,
    onCustomizeAppearance: () -> Unit,
) {
    var selectedLocale by remember { mutableStateOf(playgroundLocaleOptions.first()) }
    var selectedStyle by remember { mutableStateOf(playgroundStyleOptions.first()) }
    var selectedButtonType by remember { mutableStateOf(playgroundButtonTypeOptions.first()) }
    var selectedValidation by remember { mutableStateOf(playgroundValidationOptions.first()) }
    var selectedGrouping by remember { mutableStateOf(playgroundGroupingOptions.first()) }
    var submitVisible by remember { mutableStateOf(true) }
    var googlePayEnabled by remember { mutableStateOf(true) }
    var savedCardsEnabled by remember { mutableStateOf(true) }
    var saveCardOptInVisible by remember { mutableStateOf(true) }
    var cardHolderVerificationEnabled by remember { mutableStateOf(false) }
    var walletColor by remember { mutableStateOf(playgroundWalletColorOptions.first()) }
    var walletType by remember { mutableStateOf(playgroundWalletTypeOptions.first()) }
    var walletRadius by remember { mutableFloatStateOf(28f) }
    var mode by remember { mutableStateOf(IntegrationMode.PaymentSheet) }
    var amountMinor by remember { mutableLongStateOf(SAMPLE_AMOUNT_MINOR) }

    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var inlineOrderConsumed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingOrder by remember { mutableStateOf<PaymentIntent?>(null) }
    val scope = rememberCoroutineScope()
    val sdkStyle = playgroundResolvedStyle(UserInterfaceStyle.from(selectedStyle.value))
    val wallet = WalletAppearance(
        color = when (walletColor.value) {
            "black" -> WalletButtonColor.BLACK
            "white" -> WalletButtonColor.WHITE
            else -> null
        },
        radius = walletRadius,
        type = WalletButtonType.from(walletType.value),
    )
    val appearance = appearanceState.toAppearanceConfig()
    val structuralKey = listOf(
        googlePayEnabled,
        savedCardsEnabled,
        saveCardOptInVisible,
        selectedGrouping.value,
        cardHolderVerificationEnabled,
        selectedButtonType.value,
        selectedValidation.value,
        submitVisible,
    ).joinToString("|")

    val configuration = remember(
        selectedLocale,
        appearanceState.appearanceSignature,
        selectedButtonType,
        selectedValidation,
        selectedGrouping,
        submitVisible,
        googlePayEnabled,
        savedCardsEnabled,
        saveCardOptInVisible,
        cardHolderVerificationEnabled,
        sdkStyle,
        wallet,
    ) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(
                    enabled = googlePayEnabled,
                    appearance = wallet,
                ),
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
                    grouping = CardGrouping.from(selectedGrouping.value),
                ),
                validationMode = ValidationMode.from(selectedValidation.value),
                submitButton = SubmitButtonConfig(
                    visible = submitVisible,
                    type = SubmitButtonType.from(selectedButtonType.value),
                ),
            ),
            options = OptionsConfig(
                locale = selectedLocale.value,
                style = sdkStyle,
                appearance = appearance,
            ),
        )
    }

    fun handleInlineResult(result: PaymentResult) {
        lastResult = result
        pendingOrder = null
        ready = false
        inlineOrderConsumed = true
    }

    val checkout = rememberPaymentSheet(
        configuration = configuration,
        onResult = {
            lastResult = it
            isLoading = false
        },
    )
    val googlePay = key(structuralKey) {
        rememberGooglePay(
            configuration = configuration,
            onResult = { handleInlineResult(it) },
        )
    }
    val embedded = key(structuralKey) {
        rememberEmbeddedPayment(
            configuration = configuration,
            onResult = { handleInlineResult(it) },
        )
    }
    LaunchedEffect(appearance) { embedded.updateAppearance(appearance) }
    LaunchedEffect(sdkStyle) { embedded.updateStyle(sdkStyle) }
    LaunchedEffect(selectedLocale.value) { embedded.updateLocale(selectedLocale.value) }
    LaunchedEffect(wallet) {
        embedded.updateWalletAppearance(wallet)
        googlePay.updateAppearance(wallet)
    }

    suspend fun createOrder(): PaymentIntent =
        DemoCheckoutBackend.createPaymentIntent(
            amountMinor = amountMinor,
            description = BuildConfig.DESCRIPTION,
        )

    fun prepareInlineSurface() {
        DemoCheckoutBackend.secretsError()?.let {
            error = it
            pendingOrder = null
            return
        }
        scope.launch {
            isLoading = true
            error = null
            lastResult = null
            ready = false
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
        structuralKey,
    ) {
        error = null
        lastResult = null
        ready = false
        inlineOrderConsumed = false
        if (mode != IntegrationMode.GooglePay && mode != IntegrationMode.Embedded) {
            return@LaunchedEffect
        }
        val secretsError = DemoCheckoutBackend.secretsError()
        if (secretsError != null) {
            error = secretsError
            return@LaunchedEffect
        }
        isLoading = true
        try {
            pendingOrder = createOrder()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingOrder = null
            error = e.message ?: "Failed to create order"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(amountMinor) {
        if (mode != IntegrationMode.GooglePay && mode != IntegrationMode.Embedded) {
            return@LaunchedEffect
        }
        if (pendingOrder == null || inlineOrderConsumed) return@LaunchedEffect
        delay(300)
        error = null
        try {
            // Keep the mounted Element / wallet button; they call updateOrder
            // when [pendingOrder] changes.
            pendingOrder = createOrder()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to create order"
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExampleTopBar(
                title = "Playground",
                subtitle = "Every PaymentConfig option, then try Sheet, Google Pay, or Embedded.",
                onBack = onBack,
            )

            PlaygroundSection(
                title = "Order",
                caption = "Demo order · live API",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = BuildConfig.DESCRIPTION.ifBlank { "Checkout item" },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMoney(amountMinor, BuildConfig.CURRENCY),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                PlaygroundAmountStepper(
                    amountMinor = amountMinor,
                    enabled = lastResult == null,
                    onAmountChange = { amountMinor = it },
                )
            }

            PlaygroundSection(
                title = "Options",
                caption = "Locale and UI style for the payment form.",
            ) {
                PlaygroundDropdown(
                    label = "Language",
                    caption = "options.locale · ${selectedLocale.value}",
                    options = playgroundLocaleOptions,
                    selected = selectedLocale,
                    onSelected = { selectedLocale = it },
                )
                PlaygroundLabeledBlock(
                    label = "Style",
                    caption = "Auto follows the example theme toggle. Light and Dark force the SDK independently of app chrome.",
                ) {
                    PlaygroundSegmentedRow(
                        options = playgroundStyleOptions,
                        selected = selectedStyle,
                        onSelected = { selectedStyle = it },
                    )
                }
            }

            PlaygroundSection(
                title = "Card",
                caption = "Card form layout, validation, Pay button, and saved cards.",
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlaygroundLabeledBlock(
                        label = "Inputs",
                        caption = "card.inputs.grouping · condensed packs fields; spaced uses separate labels.",
                    ) {
                        PlaygroundSegmentedRow(
                            options = playgroundGroupingOptions,
                            selected = selectedGrouping,
                            onSelected = { selectedGrouping = it },
                        )
                    }
                    PlaygroundDropdown(
                        label = "Validation",
                        caption = "card.validationMode · when field errors appear.",
                        options = playgroundValidationOptions,
                        selected = selectedValidation,
                        onSelected = { selectedValidation = it },
                    )
                    PlaygroundDropdown(
                        label = "Submit button",
                        caption = "card.submitButton.type · localized Pay CTA.",
                        options = playgroundButtonTypeOptions,
                        selected = selectedButtonType,
                        onSelected = { selectedButtonType = it },
                    )
                }
                ExampleSwitchRow(
                    title = "Show Pay button",
                    subtitle = if (mode == IntegrationMode.Embedded) {
                        "card.submitButton.visible · Embedded only."
                    } else {
                        "Sheet always shows the SDK Pay button."
                    },
                    checked = if (mode == IntegrationMode.Embedded) submitVisible else true,
                    onCheckedChange = { submitVisible = it },
                    enabled = mode == IntegrationMode.Embedded,
                )
                ExampleSwitchRow(
                    title = "Saved cards",
                    subtitle = "card.savedCards.enabled · load and offer stored cards",
                    checked = savedCardsEnabled,
                    onCheckedChange = { savedCardsEnabled = it },
                    showDivider = true,
                )
                ExampleSwitchRow(
                    title = "Save card opt-in",
                    subtitle = "card.savedCards.optInVisible · checkbox to save a new card",
                    checked = saveCardOptInVisible,
                    onCheckedChange = { saveCardOptInVisible = it },
                    showDivider = true,
                    enabled = savedCardsEnabled,
                )
                ExampleSwitchRow(
                    title = "Card holder verification",
                    subtitle = "Validate name via account-validation before pay",
                    checked = cardHolderVerificationEnabled,
                    onCheckedChange = { cardHolderVerificationEnabled = it },
                    showDivider = true,
                )
            }

            PlaygroundSection(
                title = "Google Pay",
                caption = "Wallet button and WalletAppearance chrome.",
                contentPadding = PaddingValues(0.dp),
            ) {
                ExampleSwitchRow(
                    title = "Enabled",
                    subtitle = "paymentMethods.googlePay.enabled",
                    checked = googlePayEnabled,
                    onCheckedChange = { googlePayEnabled = it },
                )
                Column(
                    modifier = Modifier
                        .alpha(if (googlePayEnabled) 1f else 0.45f)
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlaygroundLabeledBlock(
                        label = "Button color",
                        caption = "WalletAppearance.color · Auto follows the form background.",
                    ) {
                        PlaygroundSegmentedRow(
                            options = playgroundWalletColorOptions,
                            selected = walletColor,
                            onSelected = { walletColor = it },
                            enabled = googlePayEnabled,
                        )
                    }
                    PlaygroundDropdown(
                        label = "Button type",
                        caption = "WalletAppearance.type · official Google Pay label variant.",
                        options = playgroundWalletTypeOptions,
                        selected = walletType,
                        onSelected = { walletType = it },
                        enabled = googlePayEnabled,
                    )
                    PlaygroundSliderRow(
                        label = "Corner radius",
                        value = walletRadius,
                        valueRange = 0f..32f,
                        valueLabel = "${walletRadius.toInt()} dp",
                        caption = "WalletAppearance.radius · default 28 dp.",
                        onValueChange = { walletRadius = it },
                        enabled = googlePayEnabled,
                    )
                }
            }

            PlaygroundSection(
                title = "Appearance",
                caption = "SDK visual tokens. Customize opens the full AppearanceConfig editor.",
            ) {
                ClassicsGallery(
                    selectedPresetId = appearanceState.selectedPresetId,
                    onPresetSelected = { appearanceState.applyPreset(it) },
                    onCustomize = onCustomizeAppearance,
                )
            }

            PaymentSection(
                mode = mode,
                onModeChange = { mode = it },
                payLabel = payLabel,
                isLoading = isLoading,
                pendingOrder = pendingOrder,
                ready = ready,
                hasTerminalResult = lastResult != null,
                googlePay = googlePay,
                embedded = embedded,
                onPaySheet = {
                    DemoCheckoutBackend.secretsError()?.let { error = it; return@PaymentSection }
                    scope.launch {
                        isLoading = true
                        error = null
                        lastResult = null
                        try {
                            checkout.present(createOrder()) { event ->
                                if (event is PaymentSheetEvent.Ready) isLoading = false
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to open payment sheet"
                            isLoading = false
                        }
                    }
                },
                onReady = { ready = true },
                onRetryPrepare = { prepareInlineSurface() },
            )

            error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
            lastResult?.let { result ->
                ExampleResultPanel(result)
                if (mode == IntegrationMode.PaymentSheet) {
                    ExampleButton(
                        label = "New payment",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = { lastResult = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentSection(
    mode: IntegrationMode,
    onModeChange: (IntegrationMode) -> Unit,
    payLabel: String,
    isLoading: Boolean,
    pendingOrder: PaymentIntent?,
    ready: Boolean,
    hasTerminalResult: Boolean,
    googlePay: GooglePayController,
    embedded: EmbeddedPaymentController,
    onPaySheet: () -> Unit,
    onReady: () -> Unit,
    onRetryPrepare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Try it", style = MaterialTheme.typography.titleMedium)
        PlaygroundSegmentedRow(
            options = IntegrationMode.entries.map { DemoOption(it.label, it.name) },
            selected = DemoOption(mode.label, mode.name),
            onSelected = { onModeChange(IntegrationMode.valueOf(it.value)) },
        )
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
                IntegrationMode.PaymentSheet -> if (!hasTerminalResult) {
                    ExampleButton(
                        label = payLabel,
                        loading = isLoading,
                        onClick = onPaySheet,
                    )
                }
                IntegrationMode.GooglePay -> InlineMethodSurface(
                    isLoading = isLoading,
                    pendingOrder = pendingOrder,
                    ready = ready,
                    emptyHint = "Preparing Google Pay…",
                    hasTerminalResult = hasTerminalResult,
                    onRetry = onRetryPrepare,
                ) { order ->
                    GooglePayButton(
                        controller = googlePay,
                        intent = order,
                        modifier = Modifier.fillMaxWidth(),
                        onEvent = { event ->
                            if (event is GooglePayEvent.Ready) onReady()
                        },
                    )
                }
                IntegrationMode.Embedded -> InlineMethodSurface(
                    isLoading = isLoading,
                    pendingOrder = pendingOrder,
                    ready = ready,
                    emptyHint = "Preparing embedded element…",
                    hasTerminalResult = hasTerminalResult,
                    onRetry = onRetryPrepare,
                ) { order ->
                    PaymentElement(
                        controller = embedded,
                        intent = order,
                        modifier = Modifier.fillMaxWidth(),
                        onEvent = { event ->
                            if (event is EmbeddedEvent.Ready) onReady()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineMethodSurface(
    isLoading: Boolean,
    pendingOrder: PaymentIntent?,
    ready: Boolean,
    emptyHint: String,
    hasTerminalResult: Boolean,
    onRetry: () -> Unit,
    content: @Composable (PaymentIntent) -> Unit,
) {
    when {
        isLoading && pendingOrder == null -> ExampleLoader(message = emptyHint)
        pendingOrder != null -> MerchantReadyGate(ready = ready, message = emptyHint) {
            content(pendingOrder)
        }
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

private fun modeCaption(mode: IntegrationMode): String = when (mode) {
    IntegrationMode.PaymentSheet ->
        "SDK owns the full checkout UI. Tap Pay to open the payment sheet."
    IntegrationMode.GooglePay ->
        "Standalone Google Pay button embedded in your screen."
    IntegrationMode.Embedded ->
        "Payment Element in your layout. Toggle methods under Card and Google Pay."
}

@Composable
private fun PlaygroundAmountStepper(
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
