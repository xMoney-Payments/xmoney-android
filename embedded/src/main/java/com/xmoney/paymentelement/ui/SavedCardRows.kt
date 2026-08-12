package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
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
            )
            if (subtitle.isNotEmpty()) {
                SheetText(
                    subtitle,
                    theme = theme,
                    color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                    fontSize = 13f,
                    fontWeight = FontWeight.Medium,
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
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .pressScaleClickable(enabled = enabled, onClick = onSelect),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(theme.selectedBackground),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Column(modifier = Modifier.weight(1f)) {
                SheetText(
                    savedCardDisplayName(card),
                    theme = theme,
                    fontSize = 16f,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).sp,
                )
                SheetText(
                    savedCardMeta(card, locale),
                    theme = theme,
                    color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                    fontSize = 13f,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (selected) {
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
                    )
                    SheetText(
                        Strings.text("sheet.visaOrMastercard", locale),
                        theme = theme,
                        color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                        fontSize = 13f,
                        fontWeight = FontWeight.Medium,
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
