package com.xmoney.paymentsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.SavedCard
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.paymentelement.ui.PaymentForm
import com.xmoney.paymentelement.ui.UIHelpers

@Composable
internal fun PaymentSheetContent(
    config: ResolvedPaymentConfig,
    state: SheetState,
    isProcessing: Boolean,
    onPayCard: (CardInput) -> Unit,
    onSelectSaved: (SavedCard) -> Unit,
    onDeleteSaved: suspend (SavedCard) -> Unit,
    onGooglePay: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = UIHelpers.isDarkMode(config, isSystemInDarkTheme())
    val theme = remember(config, isDark) { CheckoutTheme.resolve(config, isDark) }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val scrollState = rememberScrollState()

    PaymentForm(
        config = config,
        state = state,
        isProcessing = isProcessing,
        onPayCard = onPayCard,
        onSelectSaved = onSelectSaved,
        onDeleteSaved = onDeleteSaved,
        onGooglePay = onGooglePay,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .nestedScroll(nestedScrollInterop)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .verticalScroll(scrollState),
        header = {
            SheetHandle(theme)
            SheetHeader(theme = theme, enabled = !isProcessing, onCancel = onCancel)
        },
    )
}

@Composable
private fun SheetHandle(theme: CheckoutTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(theme.sheetHandle),
        )
    }
}

@Composable
private fun SheetHeader(
    theme: CheckoutTheme,
    enabled: Boolean,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(theme.neutralChip)
                .clickable(enabled = enabled) { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                color = theme.primaryText,
                fontSize = theme.scaledSp(14f),
                fontWeight = FontWeight.Normal,
                fontFamily = theme.fontFamily,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
