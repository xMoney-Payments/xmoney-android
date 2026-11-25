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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.SavedCard

@Composable
internal fun SavedCardsSummaryRow(
    theme: CheckoutTheme,
    locale: String,
    subtitle: String,
    card: SavedCard? = null,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.rowRadius))
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(PaymentRowContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (card == null) {
            PayWithCardChip(theme)
        } else {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(theme.brandTileRadius))
                    .background(theme.brandTileBackground)
                    .border(1.dp, theme.brandChipBorder, RoundedCornerShape(theme.brandTileRadius)),
                contentAlignment = Alignment.Center,
            ) {
                CardBrandIcon(
                    brand = savedCardBrandForIcon(card),
                    theme = theme,
                    size = CardBrandIconSize.SavedCardRow,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            SheetText(
                if (card == null) {
                    Strings.text("sheet.savedCards", locale)
                } else {
                    "${savedCardIssuerLabel(card)} ${savedCardMaskedNumber(card)}"
                },
                theme = theme,
                fontSize = 16f,
                fontWeight = FontWeight.Bold,
                letterSpacing = SheetTracking,
                lineHeight = 24f,
            )
            if (subtitle.isNotEmpty()) {
                SheetText(
                    subtitle,
                    theme = theme,
                        color = theme.metaText,
                        fontSize = 14f,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16f,
                    )
            }
        }
        Image(
            painter = painterResource(R.drawable.xmoney_ic_chevron_down),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp),
            colorFilter = ColorFilter.tint(theme.iconBrand),
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
    val askTint = theme.dangerTint
    val rowShape = if (isAskingDelete) {
        RoundedCornerShape(topStart = theme.rowRadius, topEnd = theme.rowRadius)
    } else {
        RoundedCornerShape(theme.rowRadius)
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
                horizontalArrangement = Arrangement.spacedBy(if (isAskingDelete) 13.dp else 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(theme.brandTileRadius))
                        .background(theme.brandTileBackground)
                        .border(
                            1.dp,
                            theme.brandChipBorder,
                            RoundedCornerShape(theme.brandTileRadius),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CardBrandIcon(
                        brand = savedCardBrandForIcon(card),
                        theme = theme,
                        size = CardBrandIconSize.SavedCardRow,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SheetText(
                            savedCardIssuerLabel(card),
                            theme = theme,
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 16f,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = SheetTracking,
                            lineHeight = 24f,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SheetText(
                            savedCardMaskedNumber(card),
                            theme = theme,
                            fontSize = 16f,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = SheetTracking,
                            lineHeight = 24f,
                            maxLines = 1,
                        )
                    }
                    SheetText(
                        savedCardMeta(card, locale),
                        theme = theme,
                        color = theme.metaText,
                        fontSize = 14f,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16f,
                    )
                }
                if (isEditing) {
                    SavedCardTrashButton(
                        theme = theme,
                        locale = locale,
                        enabled = enabled && !isDeleting,
                        onClick = onAskDelete,
                    )
                } else {
                    RadioDot(theme, selected = selected)
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
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(theme.dangerTintStrong)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.xmoney_ic_trash),
            contentDescription = Strings.text("sheet.delete", locale),
            modifier = Modifier
                .width(15.dp)
                .height(16.dp),
            colorFilter = ColorFilter.tint(if (theme.isDark) Color(0xFFEF4444) else Color(0xFFDC2626)),
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
            .clip(RoundedCornerShape(bottomStart = theme.rowRadius, bottomEnd = theme.rowRadius))
            .background(theme.dangerTint)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
    ) {
        SheetText(
            Strings.text("sheet.removeCardConfirm", locale),
            theme = theme,
            fontSize = 16f,
            fontWeight = FontWeight.SemiBold,
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
                background = theme.errorBadge,
                contentColor = Color.White,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = onRemove,
            )
            ConfirmPill(
                theme = theme,
                title = Strings.text("sheet.keepIt", locale),
                background = theme.primaryText,
                contentColor = theme.inverseText,
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(theme.buttonRadius(48.dp)))
            .background(background)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SheetText(
            title,
            theme = theme,
            color = contentColor,
            fontSize = 14f,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun UseOtherCardRow(
    theme: CheckoutTheme,
    locale: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SavedCardRowHeight)
                .clip(RoundedCornerShape(theme.rowRadius))
                .pressScaleClickable(enabled = enabled, onClick = onClick)
                .padding(PaymentRowContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(theme.brandTileRadius))
                    .background(theme.accentIconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.xmoney_ic_plus),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(theme.iconBrand),
                )
            }
            if (selected) {
                Column(modifier = Modifier.weight(1f)) {
                    SheetText(
                        Strings.text("sheet.useAnotherCard", locale),
                        theme = theme,
                        color = theme.primaryText,
                        fontSize = 16f,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = SheetTracking,
                        lineHeight = 24f,
                    )
                    SheetText(
                        Strings.text("sheet.visaOrMastercard", locale),
                        theme = theme,
                        color = theme.metaText,
                        fontSize = 14f,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16f,
                    )
                }
                RadioDot(theme, selected = true)
            } else {
                SheetText(
                    Strings.text("sheet.useAnotherCard", locale),
                    theme = theme,
                    color = theme.textBrand,
                    fontSize = 16f,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = SheetTracking,
                    modifier = Modifier.weight(1f),
                )
                VisaMastercardMarks(
                    theme = theme,
                    size = CardBrandIconSize.UseOtherCardPair,
                )
            }
        }
    }
}

@Composable
internal fun PayWithCardChip(theme: CheckoutTheme) {
    val shape = RoundedCornerShape(theme.brandTileRadius)
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(34.dp)
            .then(
                if (theme.isDarkSurface) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 1.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = PayWithCardChipShadow,
                        spotColor = PayWithCardChipShadow,
                    )
                },
            )
            .clip(shape)
            .background(theme.accentIconBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.xmoney_ic_card_generic),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(theme.iconBrand),
        )
    }
}

@Composable
internal fun RadioDot(theme: CheckoutTheme, selected: Boolean) {
    val stroke = if (selected) theme.primary else theme.radioIdle
    val outline = selected && theme.filledBrandNeedsOutline
    Box(
        modifier = Modifier
            .size(24.dp)
            .then(
                if (outline) Modifier.border(1.dp, theme.stateIndicatorBorder, CircleShape) else Modifier,
            )
            .padding(if (outline) 1.dp else 0.dp)
            .border(2.dp, stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(theme.primary),
            )
        }
    }
}

@Composable
internal fun SelectedCheckmark(theme: CheckoutTheme) {
    RadioDot(theme, selected = true)
}

/** Figma chip shadow is 0 1 1 at 4% ink. A 1dp elevation fades an opaque color to about that. */
private val PayWithCardChipShadow = Color(0xFF16141A)


