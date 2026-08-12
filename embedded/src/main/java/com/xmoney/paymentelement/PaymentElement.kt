package com.xmoney.paymentelement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.paymentelement.ui.PaymentForm
import com.xmoney.paymentelement.ui.XCoinFlipLoader

/**
 * Merchant-hosted Payment Element. Renders available methods (Google Pay,
 * saved cards, new card) inside the app UI — same form as Payment Sheet
 * without the bottom-sheet chrome.
 */
@Composable
fun PaymentElement(
    controller: EmbeddedPaymentController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (EmbeddedEvent) -> Unit = {},
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(controller, intent) {
        controller.bind(intent) { currentOnEvent(it) }
    }

    val state = controller.sheetState
    val config = controller.paymentConfig

    when {
        state == null || config == null -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                XCoinFlipLoader(color = Color(0xFF7C4DFF))
            }
        }
        else -> {
            PaymentForm(
                config = config,
                state = state,
                isProcessing = controller.isProcessing,
                isOrderConsumed = controller.isOrderConsumed,
                onPayCard = { controller.payWithCard(it) },
                onSelectSaved = { controller.paySavedCard(it) },
                onDeleteSaved = { controller.deleteSavedCard(it) },
                onGooglePay = { controller.startGooglePay() },
                modifier = modifier,
            )
        }
    }
}
