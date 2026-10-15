package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings

@Composable
internal fun PersonalDetailsEntryRow(
    theme: CheckoutTheme,
    locale: String,
    completed: Boolean,
    showError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(theme.paymentContainerRadius)
    val showErrorBorder = showError && !completed
    val borderColor = if (showErrorBorder) theme.errorBorder else theme.containerBorder
    val borderWidth = theme.borderWidth
    Column(modifier = modifier.fillMaxWidth().padding(top = topPadding, bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(theme.componentBackground)
                .border(borderWidth, borderColor, shape)
                .pressScaleClickable(enabled = enabled, onClick = onClick)
                .padding(6.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (completed) {
                CompletedMark(theme)
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 34.dp)
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
            }
            SheetText(
                Strings.text(
                    if (completed) "personalDetails.detailsCompleted" else "personalDetails.enterDetails",
                    locale,
                ),
                theme = theme,
                color = if (completed) theme.primaryText else theme.primary,
                fontSize = 16f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01).em,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.xmoney_ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.size(width = 13.dp, height = 8.dp).rotate(-90f),
                    colorFilter = ColorFilter.tint(theme.primary),
                )
            }
        }
        if (showErrorBorder) {
            ErrorBadgeMessage(
                Strings.text("personalDetails.completeDetails", locale),
                theme,
                topPadding = 4.dp,
            )
        }
    }
}

@Composable
private fun CompletedMark(theme: CheckoutTheme) {
    Box(modifier = Modifier.size(width = 48.dp, height = 34.dp)) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(theme.neutralChip),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xmoney_ic_person),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(theme.mutedIcon),
            )
        }
        Box(
            modifier = Modifier
                .offset(x = 30.dp, y = 16.dp)
                .size(22.dp)
                .border(1.dp, theme.componentBackground, CircleShape)
                .clip(CircleShape)
                .background(theme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xmoney_ic_check),
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 8.dp),
                colorFilter = ColorFilter.tint(theme.primaryButtonText),
            )
        }
    }
}
