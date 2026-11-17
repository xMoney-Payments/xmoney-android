package com.xmoney.paymentelement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings

/** White method card with a 6dp inset; the selected option gets the tinted 12dp panel. */
@Composable
private fun PaymentOptionPanel(
    theme: CheckoutTheme,
    selected: Boolean,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outer = RoundedCornerShape(theme.paymentContainerRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(theme.componentBackground)
            .border(theme.containerBorderWidth, theme.containerBorder, outer)
            .padding(PaymentContainerInset),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SelectedPanelRadius))
                .background(if (selected) theme.selectedBackground else Color.Transparent),
            content = content,
        )
        below?.invoke(this)
    }
}

@Composable
private fun PaymentOptionRow(
    theme: CheckoutTheme,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    chip: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PaymentRowRadius))
            .pressScaleClickable(enabled = enabled && !selected, onClick = onClick)
            .padding(PaymentRowContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chip()
        Column(modifier = Modifier.weight(1f)) {
            SheetText(
                title,
                theme = theme,
                color = theme.primaryText,
                fontSize = 16f,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            SheetText(
                subtitle,
                theme = theme,
                color = theme.metaText,
                fontSize = 14f,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        RadioDot(theme, selected = selected)
    }
}

/** "Pay with card" option for checkouts without saved cards; hosts the card form while selected. */
@Composable
internal fun PayWithCardPanel(
    theme: CheckoutTheme,
    locale: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    cardFormContent: @Composable () -> Unit,
) {
    PaymentOptionPanel(theme, selected) {
        PaymentOptionRow(
            theme = theme,
            title = Strings.text("sheet.payWithCard", locale),
            subtitle = Strings.text("sheet.visaOrMastercard", locale),
            selected = selected,
            enabled = enabled,
            onClick = onSelect,
        ) { PayWithCardChip(theme) }
        if (selected) {
            Column(
                modifier = Modifier.padding(
                    start = NewCardFormHorizontalPadding,
                    end = NewCardFormHorizontalPadding,
                    top = 16.dp,
                ),
            ) {
                cardFormContent()
            }
        }
    }
}

/** "Pay with crypto" option. Selecting it shows the redirect notice. */
@Composable
internal fun CryptoOptionPanel(
    theme: CheckoutTheme,
    locale: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    PaymentOptionPanel(
        theme = theme,
        selected = selected,
        below = if (selected) {
            {
                SheetText(
                    Strings.text("sheet.cryptoRedirectNotice", locale),
                    theme = theme,
                    color = theme.metaText,
                    fontSize = 14f,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20f,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        } else {
            null
        },
    ) {
        PaymentOptionRow(
            theme = theme,
            title = Strings.text("sheet.payWithCrypto", locale),
            subtitle = Strings.text("sheet.cryptoSubtitle", locale),
            selected = selected,
            enabled = enabled,
            onClick = onSelect,
        ) { CryptoChip(theme) }
    }
}

@Composable
private fun CryptoChip(theme: CheckoutTheme) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(theme.accentIconBackground),
        contentAlignment = Alignment.Center,
    ) {
        SheetText(
            "\u20BF",
            theme = theme,
            color = theme.primary,
            fontSize = 18f,
            fontWeight = FontWeight.Bold,
            lineHeight = null,
        )
    }
}
