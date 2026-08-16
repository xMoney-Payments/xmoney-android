package com.xmoney.paymentelement.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.SavedCard

@Composable
internal fun SavedCardsSummaryRow(
    theme: CheckoutTheme,
    locale: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(PaymentRowContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(theme.neutralChip),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xmoney_ic_card_stack),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(theme.primaryText.copy(alpha = 0.5f)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            SheetText(
                Strings.text("sheet.savedCards", locale),
                theme = theme,
                fontSize = 16f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01).sp,
                lineHeight = 20f,
            )
            if (subtitle.isNotEmpty()) {
                SheetText(
                    subtitle,
                    theme = theme,
                    color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                    fontSize = 13f,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18f,
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.xmoney_ic_chevron_down),
            contentDescription = null,
            modifier = Modifier
                .width(13.dp)
                .height(8.dp),
            colorFilter = ColorFilter.tint(theme.primaryText.copy(alpha = 0.38f)),
        )
    }
}

@Composable
internal fun SavedCardPickerRow(
    card: SavedCard,
    theme: CheckoutTheme,
    locale: String,
    selected: Boolean,
    isEditing: Boolean,
    isAskingDelete: Boolean,
    isDeleting: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    val askTint = theme.errorBorder.copy(alpha = 0.08f)
    val rowShape = if (isAskingDelete) {
        RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    } else {
        RoundedCornerShape(15.dp)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .pressScaleClickable(enabled = enabled && !isDeleting, onClick = onSelect),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        when {
                            isAskingDelete -> askTint
                            selected && !isEditing -> theme.selectedBackground
                            else -> Color.Transparent
                        },
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SavedCardRowHeight)
                    .padding(PaymentRowContentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(theme.componentBackground)
                        .border(
                            theme.containerBorderWidth,
                            theme.containerBorder,
                            RoundedCornerShape(9.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CardBrandIcon(
                        brand = savedCardBrandForIcon(card),
                        size = CardBrandIconSize.SavedCardRow,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    SheetText(
                        savedCardDisplayName(card),
                        theme = theme,
                        fontSize = 16f,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.01).sp,
                        lineHeight = 20f,
                    )
                    SheetText(
                        savedCardMeta(card, locale),
                        theme = theme,
                        color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                        fontSize = 13f,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18f,
                    )
                }
                if (isEditing) {
                    SavedCardTrashButton(
                        theme = theme,
                        locale = locale,
                        enabled = enabled && !isDeleting,
                        onClick = onAskDelete,
                    )
                } else if (selected) {
                    SelectedCheckmark(theme)
                } else {
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, theme.unselectedRing, CircleShape),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = isAskingDelete,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
        ) {
            SavedCardRemoveConfirm(
                theme = theme,
                locale = locale,
                enabled = enabled && !isDeleting,
                onRemove = onConfirmDelete,
                onKeep = onCancelDelete,
            )
        }
    }
}

@Composable
private fun SavedCardTrashButton(
    theme: CheckoutTheme,
    locale: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.85f,
        animationSpec = tween(durationMillis = 200),
        label = "trashPop",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(theme.errorBorder.copy(alpha = 0.09f))
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.xmoney_ic_trash),
            contentDescription = Strings.text("sheet.delete", locale),
            modifier = Modifier
                .width(15.dp)
                .height(16.dp),
            colorFilter = ColorFilter.tint(theme.errorText),
        )
    }
}

@Composable
private fun SavedCardRemoveConfirm(
    theme: CheckoutTheme,
    locale: String,
    enabled: Boolean,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
            .background(theme.errorBorder.copy(alpha = 0.08f))
            .padding(start = 13.dp, end = 13.dp, top = 4.dp, bottom = 14.dp),
    ) {
        SheetText(
            Strings.text("sheet.removeCardConfirm", locale),
            theme = theme,
            fontSize = 15f,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfirmPill(
                theme = theme,
                title = Strings.text("sheet.remove", locale),
                background = theme.errorText,
                contentColor = Color.White,
                fontWeight = FontWeight.Bold,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = onRemove,
            )
            ConfirmPill(
                theme = theme,
                title = Strings.text("sheet.keepIt", locale),
                background = theme.primaryText.copy(alpha = 0.06f),
                contentColor = theme.primaryText,
                fontWeight = FontWeight.SemiBold,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = onKeep,
            )
        }
    }
}

@Composable
private fun ConfirmPill(
    theme: CheckoutTheme,
    title: String,
    background: Color,
    contentColor: Color,
    fontWeight: FontWeight,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(background)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SheetText(
            title,
            theme = theme,
            color = contentColor,
            fontSize = 14.5f,
            fontWeight = fontWeight,
            letterSpacing = (-0.01).sp,
        )
    }
}

@Composable
internal fun UseOtherCardRow(
    theme: CheckoutTheme,
    locale: String,
    selected: Boolean,
    enabled: Boolean,
    showTopBorder: Boolean,
    horizontalContentPadding: Dp = PaymentRowContentPadding,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTopBorder) {
            Divider(color = theme.footerBorder, thickness = 1.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .pressScaleClickable(enabled = enabled, onClick = onClick)
                .padding(
                    horizontal = horizontalContentPadding,
                    vertical = PaymentRowContentPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(theme.accentIconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.xmoney_ic_plus),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(theme.primary),
                )
            }
            if (selected) {
                Column(modifier = Modifier.weight(1f)) {
                    SheetText(
                        Strings.text("sheet.useOtherCard", locale),
                        theme = theme,
                        color = theme.primaryText,
                        fontSize = 16f,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.01).sp,
                        lineHeight = 20f,
                    )
                    SheetText(
                        Strings.text("sheet.visaOrMastercard", locale),
                        theme = theme,
                        color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                        fontSize = 13f,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18f,
                    )
                }
                SelectedCheckmark(theme)
            } else {
                SheetText(
                    Strings.text("sheet.useOtherCard", locale),
                    theme = theme,
                    color = theme.primary,
                    fontSize = 16f,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).sp,
                    modifier = Modifier.weight(1f),
                )
                VisaMastercardMarks(
                    size = CardBrandIconSize.UseOtherCardPair,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun SelectedCheckmark(theme: CheckoutTheme) {
    Box(
        modifier = Modifier
            .size(21.dp)
            .clip(CircleShape)
            .background(theme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.xmoney_ic_check),
            contentDescription = null,
            modifier = Modifier
                .width(12.dp)
                .height(9.dp),
        )
    }
}
