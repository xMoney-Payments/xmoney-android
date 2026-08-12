package com.xmoney.googlepay.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.pay.button.ButtonTheme
import com.google.pay.button.ButtonType
import com.google.pay.button.PayButton
import com.xmoney.payments.config.WalletAppearance
import com.xmoney.payments.config.WalletButtonColor
import com.xmoney.payments.config.WalletButtonType

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun GooglePayButton(
    appearance: WalletAppearance,
    allowedPaymentMethods: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkBackground: Boolean = false,
) {
    var useFallback by remember { mutableStateOf(false) }
    val radius = (appearance.radius ?: 28f).dp
    val buttonTheme = resolveButtonTheme(appearance.color, isDarkBackground)
    val buttonType = resolveButtonType(appearance.type)

    if (!useFallback) {
        PayButton(
            onClick = onClick,
            allowedPaymentMethods = allowedPaymentMethods,
            modifier = modifier.fillMaxWidth().height(56.dp),
            theme = buttonTheme,
            type = buttonType,
            radius = radius,
            enabled = enabled,
            onError = { useFallback = true },
        )
    } else {
        val gpColor = if (buttonTheme == ButtonTheme.Light) Color.White else Color.Black
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(radius),
            colors = ButtonDefaults.buttonColors(backgroundColor = gpColor),
        ) {
            Text(
                "Pay with Google Pay",
                color = if (gpColor == Color.White) Color.Black else Color.White,
            )
        }
    }
}

internal fun resolveButtonTheme(color: WalletButtonColor?, isDarkBackground: Boolean): ButtonTheme =
    when (color) {
        WalletButtonColor.WHITE -> ButtonTheme.Light
        WalletButtonColor.BLACK -> ButtonTheme.Dark
        null -> if (isDarkBackground) ButtonTheme.Light else ButtonTheme.Dark
    }

internal fun resolveButtonType(type: WalletButtonType?): ButtonType =
    when (type) {
        WalletButtonType.BOOK -> ButtonType.Book
        WalletButtonType.BUY -> ButtonType.Buy
        WalletButtonType.CHECKOUT -> ButtonType.Checkout
        WalletButtonType.DONATE -> ButtonType.Donate
        WalletButtonType.ORDER -> ButtonType.Order
        WalletButtonType.PAY -> ButtonType.Pay
        WalletButtonType.SUBSCRIBE -> ButtonType.Subscribe
        WalletButtonType.PLAIN, null -> ButtonType.Plain
    }
