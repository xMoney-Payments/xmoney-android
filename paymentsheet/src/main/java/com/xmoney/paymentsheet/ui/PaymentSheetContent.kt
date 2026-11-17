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
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.SavedCard
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.paymentelement.ui.PaymentForm
import com.xmoney.paymentelement.ui.PersonalDetailsPageHeader
import com.xmoney.paymentelement.ui.UIHelpers

@Composable
internal fun PaymentSheetContent(
    config: ResolvedPaymentConfig,
    state: SheetState,
    isProcessing: Boolean,
    onPayCard: (CardInput, Map<String, String>?) -> Unit,
    onSelectSaved: (SavedCard, Map<String, String>?) -> Unit,
    onPayCrypto: (Map<String, String>?) -> Unit,
    onDeleteSaved: suspend (SavedCard) -> Unit,
    onGooglePay: () -> Unit,
    onCancel: () -> Unit,
    surfaceEpoch: Int = 0,
    onSurfaceDrawn: () -> Unit = {},
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
        onPayCrypto = onPayCrypto,
        onDeleteSaved = onDeleteSaved,
        onGooglePay = onGooglePay,
        surfaceEpoch = surfaceEpoch,
        onSurfaceDrawn = onSurfaceDrawn,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .nestedScroll(nestedScrollInterop)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .verticalScroll(scrollState),
        header = {
            SheetHandle(theme)
            SheetHeader(
                theme = theme,
                locale = config.options.locale,
                enabled = !isProcessing,
                onCancel = onCancel,
            )
        },
        personalDetailsHeader = { onBack, title ->
            SheetHandle(theme)
            PersonalDetailsPageHeader(
                theme = theme,
                locale = config.options.locale,
                enabled = !isProcessing,
                title = title,
                onBack = onBack,
            )
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
    locale: String,
    enabled: Boolean,
    onCancel: () -> Unit,
) {
    val closeLabel = Strings.text("sheet.cancel", locale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = enabled) { onCancel() }
                .semantics {
                    contentDescription = closeLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xmoney_ic_close),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(theme.primaryText),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
