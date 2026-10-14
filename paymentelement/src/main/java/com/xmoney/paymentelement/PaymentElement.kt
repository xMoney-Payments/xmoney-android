package com.xmoney.paymentelement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.paymentelement.ui.PaymentForm
import com.xmoney.paymentelement.ui.UIHelpers
import com.xmoney.paymentelement.ui.XCoinFlipLoader
import com.xmoney.paymentelement.ui.awaitPreDrawOrTimeout
import kotlinx.coroutines.CancellationException

/**
 * Merchant-hosted Payment Element. Renders available methods (Google Pay,
 * saved cards, new card) inside the app UI — same form as Payment Sheet
 * without the bottom-sheet chrome.
 *
 * When [intent] changes, calls [EmbeddedPaymentController.updateOrder].
 * Pay stays locked until that returns. [EmbeddedEvent.Ready] is emitted once per
 * mounted surface, after the card form and, when offered, the Google Pay button have
 * first pre-drawn. A later [intent] change does not emit it again.
 */
@Composable
fun PaymentElement(
    controller: EmbeddedPaymentController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (EmbeddedEvent) -> Unit = {},
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    var surfaceEpoch by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var readyEmitted by remember { mutableStateOf(false) }
    var coverHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val view = LocalView.current
    val isProcessing = controller.isProcessing
    LaunchedEffect(controller, intent, isProcessing) {
        try {
            controller.updateOrder(intent) { event ->
                if (event !is EmbeddedEvent.Ready) currentOnEvent(event)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaymentError.Payment) {
            return@LaunchedEffect
        } catch (_: Exception) {
            if (controller.bindError != null && controller.sheetState == null) {
                surfaceEpoch++
            }
            return@LaunchedEffect
        }
        surfaceEpoch++
    }
    LaunchedEffect(controller.bindError, surfaceEpoch) {
        if (controller.bindError == null || controller.sheetState != null || surfaceEpoch <= 0) {
            return@LaunchedEffect
        }
        view.awaitPreDrawOrTimeout()
        if (!revealed) revealed = true
        if (!readyEmitted) {
            readyEmitted = true
            currentOnEvent(EmbeddedEvent.Ready)
        }
    }

    val state = controller.sheetState
    val config = controller.paymentConfig
    val heightLocked = !revealed && coverHeight > 0.dp

    Box(
        modifier
            .fillMaxWidth()
            .then(if (heightLocked) Modifier.height(coverHeight).clipToBounds() else Modifier),
    ) {
        if (state != null && config != null && (revealed || heightLocked)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (heightLocked) {
                            Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (!revealed) Modifier.clearAndSetSemantics { } else Modifier,
                    ),
            ) {
                PaymentForm(
                    config = config,
                    state = state,
                    isProcessing = controller.isProcessing,
                    isUpdatingOrder = controller.isUpdatingOrder,
                    isOrderConsumed = controller.isOrderConsumed,
                    onPayCard = { controller.payWithCard(it) },
                    onSelectSaved = { controller.paySavedCard(it) },
                    onDeleteSaved = { controller.deleteSavedCard(it) },
                    onGooglePay = { controller.startGooglePay() },
                    onBindSubmit = { controller.bindSubmitHandler(it) },
                    modifier = Modifier.fillMaxWidth(),
                    surfaceEpoch = surfaceEpoch,
                    onSurfaceDrawn = {
                        if (!revealed) {
                            revealed = true
                        }
                        if (!readyEmitted) {
                            readyEmitted = true
                            currentOnEvent(EmbeddedEvent.Ready)
                        }
                    },
                )
            }
        } else if (controller.bindError != null) {
            Text(
                text = controller.bindError ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }
        if (!revealed) {
            val isDark = if (config != null) {
                UIHelpers.isDarkMode(config, isSystemInDarkTheme())
            } else {
                isSystemInDarkTheme()
            }
            val cover = if (config != null) CheckoutTheme.resolve(config, isDark).background else null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        val height = with(density) { size.height.toDp() }
                        if (height > 1.dp && coverHeight == 0.dp) coverHeight = height
                    }
                    .then(if (cover != null) Modifier.background(cover) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .clearAndSetSemantics { }
                    .padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                XCoinFlipLoader(color = CheckoutTheme.BrandPrimary)
            }
        }
    }
}
