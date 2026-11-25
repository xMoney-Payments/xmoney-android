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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.xmoney.payments.config.CryptoConfig
import com.xmoney.payments.config.CustomerDetailsConfig
import com.xmoney.payments.config.CustomerDetailsDefaults
import com.xmoney.payments.config.CustomerDetailsFields
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
import com.xmoney.payments.config.ColorSchemePreference
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
    var selectedButtonType by remember { mutableStateOf(playgroundButtonTypeOptions.first()) }
    var selectedValidation by remember { mutableStateOf(playgroundValidationOptions.first()) }
    var selectedGrouping by remember { mutableStateOf(playgroundGroupingOptions.first()) }
    var submitVisible by remember { mutableStateOf(true) }
    var googlePayEnabled by remember { mutableStateOf(true) }
    var cryptoEnabled by remember { mutableStateOf(false) }
    var savedCardsEnabled by remember { mutableStateOf(true) }
    var saveCardOptInVisible by remember { mutableStateOf(true) }
    var cardHolderVerificationEnabled by remember { mutableStateOf(false) }
    var customerDetailsCard by remember { mutableStateOf(false) }
    var customerDetailsGooglePay by remember { mutableStateOf(false) }
    var customerDetailsFieldsOverride by remember { mutableStateOf(false) }
    var customerDetailsFullName by remember { mutableStateOf(true) }
    var customerDetailsEmail by remember { mutableStateOf(true) }
    var customerDetailsPhone by remember { mutableStateOf(false) }
    var customerDetailsAddress by remember { mutableStateOf(true) }
    var customerDetailsPrefill by remember { mutableStateOf(false) }
    var defaultsRevision by remember { mutableIntStateOf(0) }
    var defaultFullName by remember { mutableStateOf("Jane Doe") }
    var defaultEmail by remember { mutableStateOf("jane@example.com") }
    var defaultPhone by remember { mutableStateOf("+14155550100") }
    var defaultCountry by remember { mutableStateOf("US") }
    var defaultRegion by remember { mutableStateOf("CA") }
    var defaultCity by remember { mutableStateOf("San Francisco") }
    var defaultPostalCode by remember { mutableStateOf("94105") }
    var defaultAddress by remember { mutableStateOf("1 Market St") }
    var walletColor by remember { mutableStateOf(playgroundWalletColorOptions.first()) }
    var walletType by remember { mutableStateOf(playgroundWalletTypeOptions.first()) }
    var walletRadius by remember { mutableFloatStateOf(26f) }
    var mode by remember { mutableStateOf(IntegrationMode.PaymentSheet) }
    var amountMinor by remember { mutableLongStateOf(SAMPLE_AMOUNT_MINOR) }

    var lastResult by remember { mutableStateOf<PaymentResult?>(null) }
    var inlineOrderConsumed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingOrder by remember { mutableStateOf<PaymentIntent?>(null) }
    val scope = rememberCoroutineScope()
    val selectedStyle = playgroundStyleOptions.option(appearanceState.style)
    val sdkStyle = playgroundResolvedStyle(ColorSchemePreference.from(appearanceState.style))
    val styledAppearance = appearanceState.toAppearance().copy(colorScheme = sdkStyle)
    val wallet = WalletAppearance(
        color = when (walletColor.value) {
            "black" -> WalletButtonColor.BLACK
            "white" -> WalletButtonColor.WHITE
            else -> null
        },
        radius = walletRadius,
        type = WalletButtonType.from(walletType.value),
    )
    val structuralKey = listOf(
        googlePayEnabled,
        cryptoEnabled,
        savedCardsEnabled,
        saveCardOptInVisible,
        selectedGrouping.value,
        cardHolderVerificationEnabled,
        customerDetailsCard,
        customerDetailsGooglePay,
        customerDetailsFieldsOverride,
        customerDetailsFullName,
        customerDetailsEmail,
        customerDetailsPhone,
        customerDetailsAddress,
        customerDetailsPrefill,
        defaultsRevision,
        selectedButtonType.value,
        selectedValidation.value,
        submitVisible,
    ).joinToString("|")

    val configuration = remember(
        selectedLocale,
        styledAppearance,
        selectedButtonType,
        selectedValidation,
        selectedGrouping,
        submitVisible,
        googlePayEnabled,
        cryptoEnabled,
        savedCardsEnabled,
        saveCardOptInVisible,
        cardHolderVerificationEnabled,
        customerDetailsCard,
        customerDetailsGooglePay,
        customerDetailsFieldsOverride,
        customerDetailsFullName,
        customerDetailsEmail,
        customerDetailsPhone,
        customerDetailsAddress,
        customerDetailsPrefill,
        defaultsRevision,
        defaultFullName,
        defaultEmail,
        defaultPhone,
        defaultCountry,
        defaultRegion,
        defaultCity,
        defaultPostalCode,
        defaultAddress,
        wallet,
    ) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(
                    enabled = googlePayEnabled,
                    appearance = wallet,
                ),
                crypto = CryptoConfig(enabled = cryptoEnabled),
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
            customerDetails = CustomerDetailsConfig(
                card = customerDetailsCard,
                googlePay = customerDetailsGooglePay,
                fields = if (customerDetailsFieldsOverride) {
                    CustomerDetailsFields(
                        fullName = customerDetailsFullName,
                        email = customerDetailsEmail,
                        phone = customerDetailsPhone,
                        address = customerDetailsAddress,
                    )
                } else {
                    null
                },
                defaultValues = if (customerDetailsPrefill) {
                    CustomerDetailsDefaults(
                        fullName = defaultFullName,
                        email = defaultEmail,
                        phone = defaultPhone,
                        country = defaultCountry,
                        region = defaultRegion,
                        city = defaultCity,
                        postalCode = defaultPostalCode,
                        address = defaultAddress,
                    )
                } else {
                    null
                },
            ),
            options = OptionsConfig(
                locale = selectedLocale.value,
                appearance = styledAppearance,
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
    LaunchedEffect(styledAppearance) { embedded.updateAppearance(styledAppearance) }
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
        error = null
        try {
            // Pay is locked from here, including the debounce and the server
            // call, until the new order is bound. Keep the mounted Element /
            // wallet button; their bind of the returned intent is then a no-op.
            val create: suspend () -> PaymentIntent = {
                delay(300)
                createOrder()
            }
            pendingOrder = if (mode == IntegrationMode.Embedded) {
                embedded.updateOrder(createOrder = create)
            } else {
                googlePay.updateOrder(createOrder = create)
            }
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

    val scroll = rememberScrollState()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            ExampleTopBar(title = "Playground", onBack = onBack)
            PlaygroundSegmentedRow(
                options = IntegrationMode.entries.map { DemoOption(it.label, it.name) },
                selected = DemoOption(mode.label, mode.name),
                onSelected = { mode = IntegrationMode.valueOf(it.value) },
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            PlaygroundSection(title = "Amount") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatMoney(amountMinor, BuildConfig.CURRENCY),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    PlaygroundAmountStepper(
                        amountMinor = amountMinor,
                        enabled = lastResult == null,
                        onAmountChange = { amountMinor = it },
                    )
                }
            }

            PlaygroundSection(title = "Theme") {
                PlaygroundLabeledBlock(label = "Style") {
                    PlaygroundSegmentedRow(
                        options = playgroundStyleOptions,
                        selected = selectedStyle,
                        onSelected = { appearanceState.updateStyle(it.value) },
                    )
                }
                PlaygroundPresetSwatches(
                    selectedPresetId = appearanceState.selectedPresetId,
                    onPresetSelected = { appearanceState.applyPreset(it) },
                )
                TextButton(onClick = onCustomizeAppearance) { Text("Customize") }
            }

            PlaygroundSection(
                title = "Payment methods",
                contentPadding = PaddingValues(0.dp),
            ) {
                ExampleSwitchRow(
                    title = "Google Pay",
                    subtitle = "",
                    checked = googlePayEnabled,
                    onCheckedChange = { googlePayEnabled = it },
                )
                ExampleSwitchRow(
                    title = "Crypto",
                    subtitle = "",
                    checked = cryptoEnabled,
                    onCheckedChange = { cryptoEnabled = it },
                    showDivider = true,
                )
                ExampleSwitchRow(
                    title = "Saved cards",
                    subtitle = "",
                    checked = savedCardsEnabled,
                    onCheckedChange = { savedCardsEnabled = it },
                    showDivider = true,
                )
                if (savedCardsEnabled) {
                    ExampleSwitchRow(
                        title = "Save card",
                        subtitle = "",
                        checked = saveCardOptInVisible,
                        onCheckedChange = { saveCardOptInVisible = it },
                        showDivider = true,
                    )
                }
            }

            PlaygroundSection(
                title = "Card",
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PlaygroundDropdown(
                        label = "Language",
                        options = playgroundLocaleOptions,
                        selected = selectedLocale,
                        onSelected = { selectedLocale = it },
                    )
                    PlaygroundLabeledBlock(label = "Grouping") {
                        PlaygroundSegmentedRow(
                            options = playgroundGroupingOptions,
                            selected = selectedGrouping,
                            onSelected = { selectedGrouping = it },
                        )
                    }
                    PlaygroundDropdown(
                        label = "Validation",
                        options = playgroundValidationOptions,
                        selected = selectedValidation,
                        onSelected = { selectedValidation = it },
                    )
                    PlaygroundDropdown(
                        label = "Pay button",
                        options = playgroundButtonTypeOptions,
                        selected = selectedButtonType,
                        onSelected = { selectedButtonType = it },
                    )
                }
                ExampleSwitchRow(
                    title = "Show Pay button",
                    subtitle = if (mode == IntegrationMode.Embedded) "" else "Sheet always shows it",
                    checked = if (mode == IntegrationMode.Embedded) submitVisible else true,
                    onCheckedChange = { submitVisible = it },
                    enabled = mode == IntegrationMode.Embedded,
                )
                ExampleSwitchRow(
                    title = "Name check",
                    subtitle = "",
                    checked = cardHolderVerificationEnabled,
                    onCheckedChange = { cardHolderVerificationEnabled = it },
                    showDivider = true,
                )
            }

            PlaygroundSection(
                title = "Customer",
                contentPadding = PaddingValues(0.dp),
            ) {
                ExampleSwitchRow(
                    title = "Card details",
                    subtitle = "",
                    checked = customerDetailsCard,
                    onCheckedChange = { customerDetailsCard = it },
                )
                ExampleSwitchRow(
                    title = "Google Pay details",
                    subtitle = "",
                    checked = customerDetailsGooglePay,
                    onCheckedChange = { customerDetailsGooglePay = it },
                    showDivider = true,
                    enabled = googlePayEnabled,
                )
                ExampleSwitchRow(
                    title = "Custom field list",
                    subtitle = "",
                    checked = customerDetailsFieldsOverride,
                    onCheckedChange = { customerDetailsFieldsOverride = it },
                    showDivider = true,
                )
                if (customerDetailsFieldsOverride) {
                    ExampleSwitchRow(
                        title = "Name",
                        subtitle = "",
                        checked = customerDetailsFullName,
                        onCheckedChange = { customerDetailsFullName = it },
                        showDivider = true,
                    )
                    ExampleSwitchRow(
                        title = "Email",
                        subtitle = "",
                        checked = customerDetailsEmail,
                        onCheckedChange = { customerDetailsEmail = it },
                        showDivider = true,
                    )
                    ExampleSwitchRow(
                        title = "Phone",
                        subtitle = "",
                        checked = customerDetailsPhone,
                        onCheckedChange = { customerDetailsPhone = it },
                        showDivider = true,
                    )
                    ExampleSwitchRow(
                        title = "Address",
                        subtitle = "",
                        checked = customerDetailsAddress,
                        onCheckedChange = { customerDetailsAddress = it },
                        showDivider = true,
                    )
                }
                ExampleSwitchRow(
                    title = "Prefill card form",
                    subtitle = "",
                    checked = customerDetailsPrefill,
                    onCheckedChange = { customerDetailsPrefill = it },
                    showDivider = true,
                )
                if (customerDetailsPrefill) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaygroundDefaultField("Full name", defaultFullName) { defaultFullName = it }
                        PlaygroundDefaultField("Email", defaultEmail) { defaultEmail = it }
                        PlaygroundDefaultField("Phone", defaultPhone) { defaultPhone = it }
                        PlaygroundDefaultField("Country", defaultCountry) { defaultCountry = it }
                        PlaygroundDefaultField("Region", defaultRegion) { defaultRegion = it }
                        PlaygroundDefaultField("City", defaultCity) { defaultCity = it }
                        PlaygroundDefaultField("Postal code", defaultPostalCode) { defaultPostalCode = it }
                        PlaygroundDefaultField("Address", defaultAddress) { defaultAddress = it }
                        TextButton(onClick = { defaultsRevision += 1 }) {
                            Text("Apply prefill")
                        }
                    }
                }
            }

            if (googlePayEnabled) {
                PlaygroundSection(title = "Wallet button") {
                    PlaygroundLabeledBlock(label = "Color") {
                        PlaygroundSegmentedRow(
                            options = playgroundWalletColorOptions,
                            selected = walletColor,
                            onSelected = { walletColor = it },
                        )
                    }
                    PlaygroundDropdown(
                        label = "Type",
                        options = playgroundWalletTypeOptions,
                        selected = walletType,
                        onSelected = { walletType = it },
                    )
                    PlaygroundSliderRow(
                        label = "Radius",
                        value = walletRadius,
                        valueRange = 0f..32f,
                        valueLabel = "${walletRadius.toInt()}",
                        onValueChange = { walletRadius = it },
                    )
                }
            }

            PaymentSection(
                mode = mode,
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
}

@Composable
private fun PaymentSection(
    mode: IntegrationMode,
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
        Text("Checkout", style = MaterialTheme.typography.titleMedium)
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
