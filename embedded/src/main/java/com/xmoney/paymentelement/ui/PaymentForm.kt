package com.xmoney.paymentelement.ui

import com.xmoney.googlepay.ui.GooglePayButton
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    onPayCard: (CardInput) -> Unit,
    onSelectSaved: (SavedCard) -> Unit,
    onDeleteSaved: (SavedCard) -> Unit = {},
    onGooglePay: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    isOrderConsumed: Boolean = false,
) {
    val isDark = UIHelpers.isDarkMode(config, isSystemInDarkTheme())
    val theme = remember(config, isDark) { CheckoutTheme.resolve(config, isDark) }
    val hasSavedCards = state.savedCards.isNotEmpty()
    val embedded = header == null
    val horizontalPadding = if (embedded) 16.dp else SheetContentHorizontalPadding
    val topPadding = if (embedded) 12.dp else SheetContentTopPadding
    val bottomPadding = if (embedded) 8.dp else SheetContentBottomPadding
    val interactionEnabled = !isProcessing && !isOrderConsumed

    var savedListExpanded by remember(hasSavedCards) { mutableStateOf(hasSavedCards) }
    var selectedMethod by remember(hasSavedCards) {
        mutableStateOf(if (hasSavedCards) PaymentMethod.SavedCard else PaymentMethod.NewCard)
    }
    var selectedSavedId by remember(state.savedCards) {
        mutableStateOf(state.savedCards.firstOrNull()?.id)
    }
    var cardInput by remember { mutableStateOf(CardInput()) }
    var cardFormValid by remember { mutableStateOf(false) }
    var showCardErrors by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            header?.invoke()

            Column(
                modifier = Modifier.padding(
                    top = topPadding,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (state.googlePayAvailable && !state.googlePayAllowedPaymentMethods.isNullOrBlank()) {
                    GooglePayButton(
                        appearance = config.paymentMethods.googlePay.appearance,
                        allowedPaymentMethods = state.googlePayAllowedPaymentMethods!!,
                        enabled = interactionEnabled,
                        onClick = onGooglePay,
                        isDarkBackground = isDark,
                    )
                    OrDivider(theme, config.options.locale)
                }

                if (hasSavedCards) {
                    PaymentMethodsContainer(
                        config = config,
                        theme = theme,
                        cards = state.savedCards,
                        savedListExpanded = savedListExpanded,
                        selectedMethod = selectedMethod,
                        selectedSavedId = selectedSavedId,
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
                            showCardErrors = false
                        },
                        cardFormContent = {
                            CardForm(
                                config = config,
                                theme = theme,
                                showHolderName = true,
                                showSaveOptIn = config.card.savedCards.optInVisible && !state.orderInfo.isVerifyCard,
                                holderFirst = true,
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
                    CardForm(
                        config = config,
                        theme = theme,
                        showHolderName = true,
                        showSaveOptIn = config.card.savedCards.optInVisible && !state.orderInfo.isVerifyCard,
                        holderFirst = true,
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
            }
        }

        PaymentFormFooter(
            theme = theme,
            config = config,
            state = state,
            isProcessing = isProcessing,
            interactionEnabled = interactionEnabled,
            hasSavedCards = hasSavedCards,
            selectedMethod = selectedMethod,
            selectedSavedId = selectedSavedId,
            cardFormValid = cardFormValid,
            onShowCardErrors = { showCardErrors = true },
            onPayCard = onPayCard,
            onSelectSaved = onSelectSaved,
            cardInput = cardInput,
            horizontalPadding = horizontalPadding,
            compact = embedded,
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
    hasSavedCards: Boolean,
    selectedMethod: PaymentMethod,
    selectedSavedId: String?,
    cardFormValid: Boolean,
    onShowCardErrors: () -> Unit,
    onPayCard: (CardInput) -> Unit,
    onSelectSaved: (SavedCard) -> Unit,
    cardInput: CardInput,
    horizontalPadding: Dp = SheetContentHorizontalPadding,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (compact) Modifier else Modifier.border(width = 1.dp, color = theme.footerBorder),
            )
            .padding(horizontal = horizontalPadding)
            .padding(
                top = if (compact) 8.dp else SheetFooterTopPadding,
                bottom = if (compact) 16.dp else SheetFooterBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SheetFooterContentSpacing),
    ) {
        if (config.card.submitButton.visible) {
            val amount = Strings.formatAmount(state.orderInfo.amount, state.orderInfo.currency)
            val payTitle = Strings.submitButtonTitle(config.card.submitButton.type.value, config.options.locale, amount)
            val useNewCard = !hasSavedCards || selectedMethod == PaymentMethod.NewCard

            SubmitButton(
                theme = theme,
                title = payTitle,
                locale = config.options.locale,
                enabled = interactionEnabled,
                isProcessing = isProcessing,
                dimmed = false,
                onClick = {
                    if (useNewCard) {
                        if (!cardFormValid) {
                            onShowCardErrors()
                        } else {
                            onPayCard(cardInput)
                        }
                    } else {
                        val saved = state.savedCards.firstOrNull { it.id == selectedSavedId }
                        if (saved != null) onSelectSaved(saved)
                    }
                },
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
            .padding(vertical = SheetOrDividerVerticalMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Divider(modifier = Modifier.weight(1f), color = theme.componentDivider.copy(alpha = 0.1f))
        SheetText(
            Strings.text("sheet.or", locale),
            theme = theme,
            color = theme.secondaryText.copy(alpha = 0.4f),
            fontSize = 13f,
            fontWeight = FontWeight.Medium,
        )
        Divider(modifier = Modifier.weight(1f), color = theme.componentDivider.copy(alpha = 0.1f))
    }
}
