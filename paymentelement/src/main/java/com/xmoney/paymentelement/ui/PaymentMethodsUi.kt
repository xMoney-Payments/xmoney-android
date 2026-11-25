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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.SavedCard

@Composable
internal fun PaymentMethodsContainer(
    config: ResolvedPaymentConfig,
    theme: CheckoutTheme,
    cards: List<SavedCard>,
    savedListExpanded: Boolean,
    selectedMethod: PaymentMethod,
    selectedSavedId: String?,
    isEditing: Boolean,
    pendingDeleteId: String?,
    deletingId: String?,
    enabled: Boolean,
    onExpandSavedCards: () -> Unit,
    onSelectSaved: (SavedCard) -> Unit,
    onSelectNewCard: () -> Unit,
    onToggleEdit: () -> Unit,
    onAskDelete: (SavedCard) -> Unit,
    onConfirmDelete: (SavedCard) -> Unit,
    onCancelDelete: () -> Unit,
    cardFormContent: @Composable () -> Unit,
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
            PaymentMethodsFrame(theme) {
                SavedCardsListHeader(
                    theme = theme,
                    locale = config.options.locale,
                    count = null,
                    isEditing = isEditing,
                    enabled = enabled,
                    onToggleEdit = onToggleEdit,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    SavedCardsExpandedList(
                        config = config,
                        theme = theme,
                        cards = cards,
                        selectedMethod = selectedMethod,
                        selectedSavedId = selectedSavedId,
                        isEditing = isEditing,
                        pendingDeleteId = pendingDeleteId,
                        deletingId = deletingId,
                        enabled = enabled,
                        onSelectSaved = onSelectSaved,
                        onSelectNewCard = onSelectNewCard,
                        onAskDelete = onAskDelete,
                        onConfirmDelete = onConfirmDelete,
                        onCancelDelete = onCancelDelete,
                    )
                }
            }
        } else {
            PaymentMethodsFrame(theme) {
                SavedCardsListHeader(
                    theme = theme,
                    locale = config.options.locale,
                    count = cards.size,
                    isEditing = false,
                    enabled = enabled,
                    onToggleEdit = {},
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    SavedCardsSummaryRow(
                        theme = theme,
                        locale = config.options.locale,
                        subtitle = cards.firstOrNull()?.let { savedCardMeta(it, config.options.locale) }.orEmpty(),
                        card = cards.firstOrNull(),
                        enabled = enabled,
                        onClick = onExpandSavedCards,
                    )
                    if (selectedMethod == PaymentMethod.NewCard) {
                        NewCardBlock(theme = theme) {
                            UseOtherCardRow(
                                theme = theme,
                                locale = config.options.locale,
                                selected = true,
                                enabled = false,
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
private fun PaymentMethodsFrame(
    theme: CheckoutTheme,
    content: @Composable () -> Unit,
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
            )
            .padding(PaymentContainerInset),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        content()
    }
}

@Composable
private fun SavedCardsListHeader(
    theme: CheckoutTheme,
    locale: String,
    count: Int?,
    isEditing: Boolean,
    enabled: Boolean,
    onToggleEdit: () -> Unit,
) {
    val title = if (count == null) {
        Strings.text("sheet.savedCards", locale)
    } else {
        "${Strings.text("sheet.savedCards", locale)} · $count"
    }
    val expanded = count == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expanded) {
                    Modifier
                        .height(SavedCardsHeaderHeight)
                        .padding(horizontal = 12.dp)
                } else {
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SheetText(
            title,
            theme = theme,
            color = theme.sectionLabel,
            fontSize = 14f,
            fontWeight = FontWeight.SemiBold,
        )
        if (expanded) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = SavedCardsHeaderHeight)
                    .height(SavedCardsHeaderHeight)
                    .pressScaleClickable(enabled = enabled, onClick = onToggleEdit),
                contentAlignment = Alignment.CenterEnd,
            ) {
                SheetText(
                    Strings.text(if (isEditing) "sheet.done" else "sheet.edit", locale),
                    theme = theme,
                    color = theme.textBrand,
                    fontSize = 14f,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val SavedCardsHeaderHeight = 44.dp

@Composable
private fun SavedCardsExpandedList(
    config: ResolvedPaymentConfig,
    theme: CheckoutTheme,
    cards: List<SavedCard>,
    selectedMethod: PaymentMethod,
    selectedSavedId: String?,
    isEditing: Boolean,
    pendingDeleteId: String?,
    deletingId: String?,
    enabled: Boolean,
    onSelectSaved: (SavedCard) -> Unit,
    onSelectNewCard: () -> Unit,
    onAskDelete: (SavedCard) -> Unit,
    onConfirmDelete: (SavedCard) -> Unit,
    onCancelDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val cardListScrollState = rememberScrollState()
        val density = LocalDensity.current
        val extra = if (pendingDeleteId != null) SavedCardConfirmPanelHeight else 0.dp
        LaunchedEffect(pendingDeleteId) {
            val index = cards.indexOfFirst { it.id == pendingDeleteId }
            if (index >= 0) {
                val offset = with(density) { (SavedCardRowHeight * index).roundToPx() }
                cardListScrollState.animateScrollTo(offset)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SavedCardRowHeight * MaxVisibleSavedCards + extra)
                .verticalScroll(cardListScrollState),
        ) {
            cards.forEach { card ->
                SavedCardPickerRow(
                    card = card,
                    theme = theme,
                    locale = config.options.locale,
                    selected = selectedMethod == PaymentMethod.SavedCard &&
                        selectedSavedId == card.id,
                    isEditing = isEditing,
                    isAskingDelete = pendingDeleteId == card.id,
                    isDeleting = deletingId == card.id,
                    enabled = enabled,
                    onSelect = { onSelectSaved(card) },
                    onAskDelete = { onAskDelete(card) },
                    onConfirmDelete = { onConfirmDelete(card) },
                    onCancelDelete = onCancelDelete,
                )
            }
        }
        if (!isEditing) {
            UseOtherCardRow(
                theme = theme,
                locale = config.options.locale,
                selected = selectedMethod == PaymentMethod.NewCard,
                enabled = enabled,
                onClick = onSelectNewCard,
            )
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
            .clip(RoundedCornerShape(SelectedPanelRadius))
            .background(theme.selectedBackground),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        content()
    }
}

internal val SelectedPanelRadius = 16.dp
