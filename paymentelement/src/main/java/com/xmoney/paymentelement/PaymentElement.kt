package com.xmoney.paymentelement

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.paymentelement.ui.LoadingCover
import com.xmoney.paymentelement.ui.PaymentForm
import com.xmoney.paymentelement.ui.UIHelpers
import com.xmoney.paymentelement.ui.hiddenFromUser
import kotlinx.coroutines.CancellationException

/**
 * Merchant-hosted Payment Element. Renders available methods (Google Pay,
 * saved cards, new card) inside the app UI — same form as Payment Sheet
 * without the bottom-sheet chrome.
 *
 * When [intent] changes, binds it on [controller]. Pay stays locked until the
 * bind finishes. An [intent] received while a payment is in progress is kept and
 * bound once the payment ends. [EmbeddedEvent.Ready] is emitted once per mounted
 * surface, after the card form and, when offered, the Google Pay button have first
 * pre-drawn. It is never emitted on the failure state; if a later bind replaces the
 * failure with the form, it is emitted then. A later [intent] change does not emit it.
 * A failed later bind emits [EmbeddedEvent.UpdateFailed] and keeps Pay locked.
 *
 * [modifier] is applied to the outer container that holds both the loading coin
 * and the form.
 */
@Composable
fun PaymentElement(
    controller: EmbeddedPaymentController,
    intent: PaymentIntent,
    modifier: Modifier = Modifier,
    onEvent: (EmbeddedEvent) -> Unit = {},
) {
    val currentOnEvent = rememberUpdatedState(onEvent)
    val forward: (EmbeddedEvent) -> Unit = remember(controller) {
        { event -> if (event !is EmbeddedEvent.Ready) currentOnEvent.value(event) }
    }
    var revealed by remember(controller) { mutableStateOf(false) }
    var readyEmitted by remember(controller) { mutableStateOf(false) }
    var coverHeight by remember(controller) { mutableStateOf(0.dp) }
    var deferredIntent by remember(controller) { mutableStateOf<PaymentIntent?>(null) }
    val density = LocalDensity.current

    suspend fun bind(target: PaymentIntent) {
        try {
            controller.rebindFromSurface(target, forward)
            if (deferredIntent == target) deferredIntent = null
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaymentError.PaymentInProgress) {
            deferredIntent = target
        } catch (_: Exception) {
            // Reported through onResult (initial bind) or EmbeddedEvent.UpdateFailed.
        }
    }

    LaunchedEffect(controller, intent) {
        deferredIntent = null
        bind(intent)
    }
    val isProcessing = controller.isProcessing
    LaunchedEffect(controller, isProcessing) {
        val pending = deferredIntent
        if (!isProcessing && pending != null) bind(pending)
    }

    val state = controller.sheetState
    val config = controller.paymentConfig
    val failed = state == null && controller.bindError != null
    val showCoin = !revealed && !failed
    val heightLocked = showCoin && coverHeight > 0.dp
    val surfaceEpoch = if (state != null) 1 else 0

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
                    .hiddenFromUser(!revealed),
            ) {
                PaymentForm(
                    config = config,
                    state = state,
                    isProcessing = controller.isProcessing,
                    isUpdatingOrder = controller.isUpdatingOrder || controller.isStaleOrder,
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
                            currentOnEvent.value(EmbeddedEvent.Ready)
                        }
                    },
                )
            }
        } else if (failed) {
            val resolved = config ?: controller.resolvedConfig(intent)
            val theme = CheckoutTheme.resolve(resolved, UIHelpers.isDarkMode(resolved, isSystemInDarkTheme()))
            Text(
                text = Strings.text("sheet.loadFailed", resolved.options.locale),
                color = theme.error,
                fontFamily = theme.fontFamily,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }
        if (showCoin) {
            val isDark = if (config != null) {
                UIHelpers.isDarkMode(config, isSystemInDarkTheme())
            } else {
                isSystemInDarkTheme()
            }
            val cover = if (config != null) CheckoutTheme.resolve(config, isDark).background else null
            LoadingCover(
                locale = (config ?: controller.resolvedConfig(intent)).options.locale,
                modifier = Modifier
                    .onSizeChanged { size ->
                        val height = with(density) { size.height.toDp() }
                        if (height > 1.dp && coverHeight == 0.dp) coverHeight = height
                    }
                    .then(if (cover != null) Modifier.background(cover) else Modifier),
            )
        }
    }
}
