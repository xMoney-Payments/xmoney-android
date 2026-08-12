package com.xmoney.paymentelement.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.SavedCard

@Composable
internal fun PaymentMethodsContainer(
    config: ResolvedPaymentConfig,
    theme: CheckoutTheme,
    cards: List<SavedCard>,
    savedListExpanded: Boolean,
    selectedMethod: PaymentMethod,
    selectedSavedId: String?,
    enabled: Boolean,
    onExpandSavedCards: () -> Unit,
    onSelectSaved: (SavedCard) -> Unit,
    onSelectNewCard: () -> Unit,
    cardFormContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.paymentContainerRadius))
            .background(theme.componentBackground)
            .border(
                theme.containerBorderWidth,
                theme.containerBorder,
                RoundedCornerShape(theme.paymentContainerRadius),
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        AnimatedContent(
            targetState = savedListExpanded,
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = SheetInDurationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(durationMillis = 180),
                    )
                    ).using(SizeTransform(clip = false))
            },
            label = "savedListExpanded",
        ) { expanded ->
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PaymentContainerInset),
                ) {
                    val cardListScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = SavedCardRowHeight * MaxVisibleSavedCards)
                            .verticalScroll(cardListScrollState),
                    ) {
                        cards.forEach { card ->
                            SavedCardPickerRow(
                                card = card,
                                theme = theme,
                                locale = config.options.locale,
                                selected = selectedMethod == PaymentMethod.SavedCard &&
                                    selectedSavedId == card.id,
                                enabled = enabled,
                                onSelect = { onSelectSaved(card) },
                            )
                        }
                    }
                    UseOtherCardRow(
                        theme = theme,
                        locale = config.options.locale,
                        selected = selectedMethod == PaymentMethod.NewCard,
                        enabled = enabled,
                        showTopBorder = true,
                        onClick = onSelectNewCard,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PaymentContainerInset),
                    ) {
                        SavedCardsSummaryRow(
                            theme = theme,
                            locale = config.options.locale,
                            subtitle = savedCardsSummarySubtitle(cards),
                            enabled = enabled,
                            onClick = onExpandSavedCards,
                        )
                    }
                    if (selectedMethod == PaymentMethod.NewCard) {
                        NewCardBlock(theme = theme) {
                            UseOtherCardRow(
                                theme = theme,
                                locale = config.options.locale,
                                selected = true,
                                enabled = false,
                                showTopBorder = false,
                                horizontalContentPadding = PaymentRowContentPadding,
                                onClick = {},
                            )
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = NewCardFormHorizontalPadding,
                                ),
                            ) {
                                cardFormContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewCardBlock(
    theme: CheckoutTheme,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    bottomStart = 15.dp,
                    bottomEnd = 15.dp,
                ),
            )
            .background(theme.selectedBackground)
            .padding(bottom = PaymentContainerInset),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        content()
    }
}
