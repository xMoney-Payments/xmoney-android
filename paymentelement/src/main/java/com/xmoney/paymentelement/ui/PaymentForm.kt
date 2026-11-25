@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.xmoney.paymentelement.ui

import androidx.compose.ui.viewinterop.AndroidView
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.customerdetails.CustomerDetailsInput
import com.xmoney.payments.engine.DigitalWalletFactory
import com.xmoney.payments.engine.WalletButtonArgs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.payments.validation.CardFieldValidators.localizedMessage
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.config.Strings
import com.xmoney.payments.config.ResolvedPaymentConfig

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun PaymentForm(
    config: ResolvedPaymentConfig,
    state: SheetState,
    isProcessing: Boolean,
    onPayCard: (CardInput, CustomerDetailsInput?) -> Unit,
    onSelectSaved: (SavedCard, CustomerDetailsInput?) -> Unit,
    onPayCrypto: (CustomerDetailsInput?) -> Unit = {},
    onDeleteSaved: suspend (SavedCard) -> Unit = {},
    onGooglePay: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    personalDetailsHeader: @Composable ((onBack: () -> Unit, title: String) -> Unit)? = null,
    savedCardsEditHeader: @Composable ((onBack: () -> Unit, title: String) -> Unit)? = null,
    isOrderConsumed: Boolean = false,
    isUpdatingOrder: Boolean = false,
    onBindSubmit: ((() -> Unit) -> Unit)? = null,
    surfaceEpoch: Int = 0,
    onSurfaceDrawn: () -> Unit = {},
) {
    val isDark = UIHelpers.isDarkMode(config, isSystemInDarkTheme())
    val context = androidx.compose.ui.platform.LocalContext.current
    val fontScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
    val contentSizeScale = maxOf(fontScale, 1f)
    val increasedContrast = rememberIncreasedContrast()
    val theme = remember(config, isDark, contentSizeScale, increasedContrast) {
        CheckoutTheme.resolve(config, isDark, contentSizeScale, increasedContrast)
    }
    val hasSavedCards = state.savedCards.isNotEmpty()
    val embedded = header == null
    val horizontalPadding = if (embedded) 0.dp else SheetContentHorizontalPadding
    val topPadding = if (embedded) 0.dp else SheetContentTopPadding
    val bottomPadding = if (embedded) 0.dp else SheetContentBottomPadding
    val interactionEnabled = !isProcessing && !isUpdatingOrder && !isOrderConsumed

    var savedListExpanded by remember(hasSavedCards) { mutableStateOf(hasSavedCards) }
    var selectedMethod by remember(hasSavedCards) {
        mutableStateOf(if (hasSavedCards) PaymentMethod.SavedCard else PaymentMethod.NewCard)
    }
    var selectedSavedId by remember {
        mutableStateOf(state.savedCards.firstOrNull()?.id)
    }
    var isEditingSavedCards by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var cardInput by remember { mutableStateOf(CardInput()) }
    var cardFormValid by remember { mutableStateOf(false) }
    var showCardErrors by remember { mutableStateOf(false) }
    val needsDetails = config.customerDetails.card && state.customerDetailsVisibility.isActive
    val detailsState = remember(config.card.validationMode) {
        PersonalDetailsFormState(state.customerDetailsVisibility, config.card.validationMode).apply {
            val defaults = if (config.customerDetails.card) state.customerDetailsDefaults else CustomerDetailsInput()
            adoptDefaults(defaults, state.customerDetailsVisibility)
        }
    }
    LaunchedEffect(state.sessionToken, state.customerDetailsDefaults) {
        val defaults = if (config.customerDetails.card) state.customerDetailsDefaults else CustomerDetailsInput()
        detailsState.adoptDefaults(defaults, state.customerDetailsVisibility)
    }
    LaunchedEffect(state.customerDetailsVisibility) {
        detailsState.revalidate(state.customerDetailsVisibility)
    }
    var showingDetails by remember { mutableStateOf(false) }
    var detailsPicker by remember { mutableStateOf<DetailsPicker?>(null) }
    var detailsAttempted by remember { mutableStateOf(false) }
    var detailsScrollNonce by remember { mutableIntStateOf(0) }
    val detailsErrorRequester = remember { BringIntoViewRequester() }
    val showDetailsError = needsDetails && detailsAttempted && !detailsState.completed
    LaunchedEffect(detailsScrollNonce) {
        if (showDetailsError) detailsErrorRequester.bringIntoView()
    }
    val deleteScope = rememberCoroutineScope()
    val savedCardIds = state.savedCards.map { it.id }
    val walletMethods = state.googlePayAllowedPaymentMethods
    val walletFactory = DigitalWalletFactory.buttonFactory
    val walletShown = state.googlePayAvailable && !walletMethods.isNullOrBlank() && walletFactory != null
    var formReady by remember { mutableStateOf(false) }
    var walletEpoch by remember { mutableIntStateOf(-1) }
    val walletHolder = remember { WalletViewHolder() }
    val walletReady = surfaceEpoch > 0 && walletEpoch == surfaceEpoch

    DisposableEffect(surfaceEpoch, walletShown) {
        val view = walletHolder.view
        val cancel = if (surfaceEpoch > 0 && walletShown && view != null) {
            val epoch = surfaceEpoch
            view.armSizedPreDraw { walletEpoch = epoch }
        } else {
            null
        }
        onDispose { cancel?.invoke() }
    }

    ReportSurfaceDraw(
        epoch = surfaceEpoch,
        formReady = formReady,
        walletShown = walletShown,
        walletReady = walletReady,
        onDrawn = onSurfaceDrawn,
    )

    LaunchedEffect(savedCardIds) {
        if (selectedSavedId != null && selectedSavedId !in savedCardIds) {
            selectedSavedId = savedCardIds.firstOrNull()
        }
        if (pendingDeleteId != null && pendingDeleteId !in savedCardIds) {
            pendingDeleteId = null
        }
        if (!hasSavedCards) {
            isEditingSavedCards = false
            pendingDeleteId = null
        }
    }
    val showCrypto = config.paymentMethods.crypto.enabled
    LaunchedEffect(showCrypto) {
        if (!showCrypto && selectedMethod == PaymentMethod.Crypto) {
            selectedMethod = if (hasSavedCards) PaymentMethod.SavedCard else PaymentMethod.NewCard
        }
    }

    val detailsTransition = updateTransition(targetState = showingDetails, label = "personalDetails")
    val cardAlpha by detailsTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = PageFadeOutMs)
            } else {
                tween(durationMillis = PageFadeInMs, delayMillis = PageFadeOutMs)
            }
        },
        label = "cardAlpha",
    ) { onDetails -> if (onDetails) 0f else 1f }
    val detailsAlpha by detailsTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = PageFadeInMs, delayMillis = PageFadeOutMs)
            } else {
                tween(durationMillis = PageFadeOutMs)
            }
        },
        label = "detailsAlpha",
    ) { onDetails -> if (onDetails) 1f else 0f }
    // Height follows the page that is still on screen, and switches only once that page has faded out.
    val detailsOnScreen = detailsAlpha > PageAlphaHidden ||
        (showingDetails && cardAlpha <= PageAlphaHidden)
    val detailsOnScreenState = rememberUpdatedState(detailsOnScreen)

    val paySelected: () -> Unit = paySelected@{
        val detailsReady = !needsDetails || detailsState.completed
        if (needsDetails && !detailsReady) {
            if (isEditingSavedCards) isEditingSavedCards = false
            detailsAttempted = true
            detailsScrollNonce += 1
        }
        if (selectedMethod == PaymentMethod.Crypto) {
            if (detailsReady) onPayCrypto(detailsState.completedInput())
            return@paySelected
        }
        val useNewCard = !hasSavedCards || selectedMethod == PaymentMethod.NewCard
        if (useNewCard) {
            if (!cardFormValid) showCardErrors = true
            if (cardFormValid && detailsReady) onPayCard(cardInput, detailsState.completedInput())
        } else if (detailsReady) {
            val saved = state.savedCards.firstOrNull { it.id == selectedSavedId }
            if (saved != null) onSelectSaved(saved, detailsState.completedInput())
        }
    }
    // Continue only saves the page and returns. Host confirm() saves a valid page and pays.
    val continueDetails: () -> Unit = {
        if (detailsState.confirm()) showingDetails = false
    }
    val submitSelected: () -> Unit = {
        val pageOpen = detailsOnScreenState.value
        val valid = if (pageOpen) detailsState.confirm() else true
        if (pageOpen && valid) showingDetails = false
        if (hostConfirmPays(pageOpen, valid)) paySelected()
    }

    DisposableEffect(
        onBindSubmit,
        hasSavedCards,
        selectedMethod,
        selectedSavedId,
        cardFormValid,
        cardInput,
        interactionEnabled,
        showingDetails,
        detailsState.completed,
        needsDetails,
    ) {
        onBindSubmit?.invoke(submitSelected)
        onDispose { onBindSubmit?.invoke {} }
    }

    val focusManager = LocalFocusManager.current
    val closePicker = {
        detailsPicker?.onDismiss?.invoke()
        detailsPicker = null
    }
    val leaveDetails = {
        detailsPicker = null
        detailsState.onLeave()
        showingDetails = false
    }
    LaunchedEffect(showingDetails) {
        if (!showingDetails) detailsPicker = null
        if (showingDetails) focusManager.clearFocus(force = true)
    }
    LaunchedEffect(state.customerDetailsVisibility.isActive) {
        if (!state.customerDetailsVisibility.isActive) {
            detailsPicker = null
            showingDetails = false
        }
    }
    val leaveSavedCardEdit = {
        isEditingSavedCards = false
        pendingDeleteId = null
    }
    BackHandler(
        enabled = isEditingSavedCards && !showingDetails && interactionEnabled,
        onBack = leaveSavedCardEdit,
    )
    BackHandler(
        enabled = showingDetails && detailsPicker != null && interactionEnabled,
        onBack = closePicker,
    )
    BackHandler(
        enabled = showingDetails && detailsPicker == null && interactionEnabled,
        onBack = leaveDetails,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (coords.size.width > 1 && coords.size.height > 1) formReady = true
            }
            .then(if (embedded) Modifier else Modifier.background(theme.background)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = SheetInDurationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier.pageLayer(
                        ownsHeight = !detailsOnScreen,
                        alpha = cardAlpha,
                    ),
                ) {
                    if (isEditingSavedCards) {
                        val editTitle = Strings.text("sheet.savedCards", config.options.locale)
                        if (savedCardsEditHeader != null) {
                            savedCardsEditHeader(leaveSavedCardEdit, editTitle)
                        } else {
                            PersonalDetailsPageHeader(
                                theme = theme,
                                locale = config.options.locale,
                                enabled = interactionEnabled,
                                title = editTitle,
                                startPadding = 0.dp,
                                onBack = leaveSavedCardEdit,
                            )
                        }
                    } else {
                        header?.invoke()
                    }
                    Column(
                        modifier = Modifier.padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = topPadding,
                            bottom = bottomPadding,
                        ),
                    ) {
                if (config.paymentMethods.googlePay.enabled &&
                    DigitalWalletFactory.makeGooglePay == null
                ) {
                    error(
                        "Google Pay is enabled but the googlepay module is not linked. " +
                            "Add the googlepay artifact so Google Pay installs on load.",
                    )
                }
                if (!isEditingSavedCards && !walletMethods.isNullOrBlank() && walletFactory != null && walletShown) {
                    val walletAppearance = config.paymentMethods.googlePay.appearance
                    val walletRadius = walletAppearance.radius ?: theme.buttonRadius(52.dp).value
                    val walletShape = RoundedCornerShape(walletRadius.dp)
                    val walletArgs = WalletButtonArgs(
                        allowedPaymentMethods = walletMethods,
                        appearance = walletAppearance.copy(radius = walletRadius),
                        enabled = interactionEnabled,
                        isDarkBackground = theme.isDarkSurface,
                        onClick = onGooglePay,
                    )
                    AndroidView(
                        factory = { context ->
                            walletFactory.create(context, walletArgs).also { walletHolder.view = it }
                        },
                        update = { view ->
                            @Suppress("UNCHECKED_CAST")
                            (view.tag as? MutableState<WalletButtonArgs>)?.value = walletArgs
                        },
                        onRelease = { walletHolder.view = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .then(
                                if (theme.isDarkSurface) {
                                    Modifier
                                } else {
                                    Modifier.shadow(
                                        elevation = 9.dp,
                                        shape = walletShape,
                                        ambientColor = WalletShadowColor,
                                        spotColor = WalletShadowColor,
                                    )
                                },
                            )
                            .clip(walletShape),
                    )
                    OrDivider(theme, config.options.locale)
                }

                if (needsDetails && !isEditingSavedCards) {
                    PersonalDetailsEntryRow(
                        theme = theme,
                        locale = config.options.locale,
                        completed = detailsState.completed,
                        showError = detailsAttempted && !detailsState.completed,
                        enabled = interactionEnabled,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            showingDetails = true
                        },
                        topPadding = 0.dp,
                        modifier = Modifier.bringIntoViewRequester(detailsErrorRequester),
                    )
                }

                if (hasSavedCards && selectedMethod == PaymentMethod.Crypto) {
                    PayWithCardPanel(
                        theme = theme,
                        locale = config.options.locale,
                        selected = false,
                        enabled = interactionEnabled,
                        onSelect = {
                            selectedSavedId = selectedSavedId ?: state.savedCards.firstOrNull()?.id
                            savedListExpanded = true
                            selectedMethod = PaymentMethod.SavedCard
                        },
                        cardFormContent = {},
                    )
                } else if (hasSavedCards) {
                    PaymentMethodsContainer(
                        config = config,
                        theme = theme,
                        cards = state.savedCards,
                        savedListExpanded = savedListExpanded,
                        selectedMethod = selectedMethod,
                        selectedSavedId = selectedSavedId,
                        isEditing = isEditingSavedCards,
                        pendingDeleteId = pendingDeleteId,
                        deletingId = deletingId,
                        enabled = interactionEnabled,
                        onExpandSavedCards = {
                            savedListExpanded = true
                            selectedMethod = PaymentMethod.SavedCard
                        },
                        onSelectSaved = { card ->
                            selectedSavedId = card.id
                            selectedMethod = PaymentMethod.SavedCard
                        },
                        onSelectNewCard = {
                            selectedMethod = PaymentMethod.NewCard
                            savedListExpanded = false
                            isEditingSavedCards = false
                            pendingDeleteId = null
                            showCardErrors = false
                        },
                        onToggleEdit = {
                            isEditingSavedCards = !isEditingSavedCards
                            pendingDeleteId = null
                        },
                        onAskDelete = { card ->
                            pendingDeleteId = card.id
                        },
                        onConfirmDelete = { card ->
                            deleteScope.launch {
                                deletingId = card.id
                                try {
                                    onDeleteSaved(card)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_ignored: Exception) {
                                    // Keep the confirm open; the card is still present.
                                } finally {
                                    deletingId = null
                                }
                            }
                        },
                        onCancelDelete = { pendingDeleteId = null },
                        cardFormContent = {
                            CardForm(
                                config = config,
                                theme = theme,
                                showHolderName = true,
                                holderFirst = true,
                                showSaveOptIn = config.card.savedCards.optInVisible && !state.orderInfo.isVerifyCard,
                                sectionLabel = if (config.card.inputs.isSpaced) {
                                    null
                                } else {
                                    Strings.text("sheet.cardDetails", config.options.locale)
                                },
                                saveCardLabel = Strings.text("sheet.saveCardShort", config.options.locale),
                                showErrors = showCardErrors,
                                onChange = { input, valid ->
                                    cardInput = input
                                    cardFormValid = valid
                                },
                            )
                        },
                    )
                } else {
                    val standaloneCardForm: @Composable () -> Unit = {
                        CardForm(
                            config = config,
                            theme = theme,
                            showHolderName = true,
                            holderFirst = true,
                            showSaveOptIn = config.card.savedCards.optInVisible && !state.orderInfo.isVerifyCard,
                            sectionLabel = if (config.card.inputs.isSpaced) {
                                null
                            } else {
                                Strings.text("sheet.cardDetails", config.options.locale)
                            },
                            saveCardLabel = Strings.text("sheet.saveCardShort", config.options.locale),
                            showErrors = showCardErrors,
                            contentTopPadding = 0.dp,
                            onChange = { input, valid ->
                                cardInput = input
                                cardFormValid = valid
                            },
                        )
                    }
                    if (showCrypto) {
                        PayWithCardPanel(
                            theme = theme,
                            locale = config.options.locale,
                            selected = selectedMethod == PaymentMethod.NewCard,
                            enabled = interactionEnabled,
                            onSelect = {
                                selectedMethod = PaymentMethod.NewCard
                                showCardErrors = false
                            },
                            cardFormContent = standaloneCardForm,
                        )
                    } else {
                        standaloneCardForm()
                    }
                }

                if (showCrypto && !isEditingSavedCards) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CryptoOptionPanel(
                        theme = theme,
                        locale = config.options.locale,
                        selected = selectedMethod == PaymentMethod.Crypto,
                        enabled = interactionEnabled,
                        onSelect = {
                            focusManager.clearFocus(force = true)
                            selectedMethod = PaymentMethod.Crypto
                            isEditingSavedCards = false
                            pendingDeleteId = null
                        },
                    )
                }
                    }
                }
                if (detailsOnScreen) {
                    PersonalDetailsLayer(
                        theme = theme,
                        config = config,
                        state = detailsState,
                        enabled = interactionEnabled,
                        embedded = embedded,
                        fillBackground = !embedded,
                        picker = detailsPicker,
                        onOpenPicker = { detailsPicker = it },
                        onClosePicker = closePicker,
                        onSelectPicker = { value ->
                            val picker = detailsPicker
                            if (picker != null) {
                                picker.onSelect(value)
                                picker.onDismiss()
                            }
                            detailsPicker = null
                        },
                        personalDetailsHeader = personalDetailsHeader,
                        horizontalPadding = horizontalPadding,
                        onBack = leaveDetails,
                        modifier = Modifier.pageLayer(
                            ownsHeight = true,
                            alpha = detailsAlpha,
                        ),
                    )
                }
            }
        }

        if (detailsPicker == null || embedded) {
        PaymentFormFooter(
            theme = theme,
            config = config,
            state = state,
            isProcessing = isProcessing,
            interactionEnabled = interactionEnabled,
            onSubmit = {
                if (isEditingSavedCards) {
                    isEditingSavedCards = false
                    pendingDeleteId = null
                } else if (detailsOnScreenState.value) {
                    continueDetails()
                } else {
                    paySelected()
                }
            },
            horizontalPadding = horizontalPadding,
            compact = embedded,
            showSubmitButton = isEditingSavedCards || !embedded || config.card.submitButton.visible,
            continueMode = detailsOnScreen,
            editingSavedCards = isEditingSavedCards,
            cryptoMode = selectedMethod == PaymentMethod.Crypto,
        )
        }
    }
}

/** Platform shadows scale spot alpha to ~0.19, which matches the Figma 18% wallet shadow. */
private val WalletShadowColor = Color(0xFF16141A)

private val PersonalDetailsHeaderStartPadding = 8.dp

private const val PageFadeOutMs = 120
private const val PageFadeInMs = 180
private const val PageAlphaHidden = 0.01f

private fun Modifier.pageLayer(ownsHeight: Boolean, alpha: Float): Modifier {
    val layer = this
        .fillMaxWidth()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = if (ownsHeight) placeable.height else 0
            layout(placeable.width, height) {
                if (ownsHeight) placeable.place(0, 0)
            }
        }
        .graphicsLayer { this.alpha = alpha }
    return if (ownsHeight && alpha > PageAlphaHidden) {
        layer
    } else {
        layer
            .clearAndSetSemantics { }
            .focusProperties { canFocus = false }
    }
}

@Composable
private fun PersonalDetailsLayer(
    theme: CheckoutTheme,
    config: ResolvedPaymentConfig,
    state: PersonalDetailsFormState,
    enabled: Boolean,
    embedded: Boolean,
    fillBackground: Boolean,
    picker: DetailsPicker?,
    onOpenPicker: (DetailsPicker) -> Unit,
    onClosePicker: () -> Unit,
    onSelectPicker: (String) -> Unit,
    personalDetailsHeader: @Composable ((onBack: () -> Unit, title: String) -> Unit)?,
    horizontalPadding: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetPicker = picker?.takeIf { !embedded }
    val headerTitle = sheetPicker?.title ?: Strings.text("personalDetails.enterDetails", config.options.locale)
    val headerBack = if (sheetPicker != null) onClosePicker else onBack
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp - 200.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillBackground) Modifier.background(theme.background) else Modifier),
    ) {
        if (sheetPicker != null) {
            PickerSheetHandle(theme)
            SelectionListHeader(
                title = sheetPicker.title,
                theme = theme,
                locale = config.options.locale,
                enabled = enabled,
                startPadding = PersonalDetailsHeaderStartPadding,
                onClose = headerBack,
            )
        } else if (personalDetailsHeader != null) {
            personalDetailsHeader(headerBack, headerTitle)
        } else {
            PersonalDetailsPageHeader(
                theme = theme,
                locale = config.options.locale,
                enabled = enabled,
                startPadding = if (embedded) 0.dp else PersonalDetailsHeaderStartPadding,
                onBack = onBack,
            )
        }
        if (picker == null || embedded) {
            PersonalDetailsForm(
                state = state,
                config = config,
                theme = theme,
                onOpenPicker = onOpenPicker,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )
        }
        if (sheetPicker != null) {
            SelectionList(
                title = sheetPicker.title,
                options = sheetPicker.options,
                selected = sheetPicker.selected,
                theme = theme,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 16.dp)
                    .height(listHeight.coerceAtLeast(280.dp)),
                onSelect = onSelectPicker,
            )
        }
        if (embedded && picker != null) {
            SelectionListSheet(
                picker = picker,
                theme = theme,
                locale = config.options.locale,
                enabled = enabled,
                onSelect = onSelectPicker,
                onDismiss = onClosePicker,
            )
        }
    }
}

@Composable
private fun PickerSheetHandle(theme: CheckoutTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(theme.sheetHandle),
        )
    }
}

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun PersonalDetailsPageHeader(
    theme: CheckoutTheme,
    locale: String,
    enabled: Boolean,
    title: String = Strings.text("personalDetails.enterDetails", locale),
    startPadding: Dp = PersonalDetailsHeaderStartPadding,
    onBack: () -> Unit,
) {
    val backLabel = Strings.text("personalDetails.back", locale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = enabled, onClick = onBack)
                .semantics {
                    contentDescription = backLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xmoney_ic_chevron_down),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 20.dp, height = 12.dp)
                    .rotate(90f)
                    .clearAndSetSemantics { },
                colorFilter = ColorFilter.tint(theme.sheetChromeText),
            )
        }
        SheetText(
            title,
            theme = theme,
            color = theme.sheetText,
            fontSize = 21f,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.03).em,
            lineHeight = 21f,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
    }
}

@Composable
private fun PaymentFormFooter(
    theme: CheckoutTheme,
    config: ResolvedPaymentConfig,
    state: SheetState,
    isProcessing: Boolean,
    interactionEnabled: Boolean,
    onSubmit: () -> Unit,
    horizontalPadding: Dp = SheetContentHorizontalPadding,
    compact: Boolean = false,
    showSubmitButton: Boolean = true,
    continueMode: Boolean = false,
    editingSavedCards: Boolean = false,
    cryptoMode: Boolean = false,
) {
    val detailsFooter = continueMode
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (detailsFooter) 16.dp else horizontalPadding)
            .padding(
                top = when {
                    detailsFooter -> 16.dp
                    else -> SheetFooterTopPadding
                },
                bottom = when {
                    detailsFooter -> 16.dp
                    compact -> 0.dp
                    else -> SheetFooterBottomPadding
                },
            ),
        verticalArrangement = Arrangement.spacedBy(if (detailsFooter) 12.dp else SheetFooterContentSpacing),
    ) {
        if (showSubmitButton) {
            val amount = Strings.formatAmount(
                state.orderInfo.amount,
                state.orderInfo.currency,
                config.options.locale,
            )
            val payTitle = when {
                continueMode -> Strings.text("personalDetails.continue", config.options.locale)
                editingSavedCards || cryptoMode -> Strings.text("button.continue", config.options.locale)
                else -> Strings.submitButtonTitle(config.card.submitButton.type.value, config.options.locale, amount)
            }

            SubmitButton(
                theme = theme,
                title = payTitle,
                locale = config.options.locale,
                enabled = interactionEnabled,
                isProcessing = isProcessing,
                onClick = onSubmit,
            )
        }

        PoweredByFooter(theme, config.options.locale)
    }
}

@Composable
private fun OrDivider(theme: CheckoutTheme, locale: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Divider(modifier = Modifier.weight(1f), color = theme.orDivider)
        SheetText(
            Strings.text("sheet.or", locale),
            theme = theme,
            color = theme.orLabel,
            fontSize = 14f,
            fontWeight = FontWeight.Normal,
        )
        Divider(modifier = Modifier.weight(1f), color = theme.orDivider)
    }
}

private class WalletViewHolder {
    var view: android.view.View? = null
}

/** Host confirm pays unless the details page is open and invalid. Continue never calls this. */
internal fun hostConfirmPays(detailsPageOpen: Boolean, detailsValid: Boolean): Boolean {
    if (!detailsPageOpen) return true
    return detailsValid
}
