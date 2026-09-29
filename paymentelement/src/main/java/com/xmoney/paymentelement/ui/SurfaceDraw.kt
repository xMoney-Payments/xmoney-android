package com.xmoney.paymentelement.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import androidx.core.view.OneShotPreDrawListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Waits until the card form has a real size and, when Google Pay is shown, the
 * wallet button has pre-drawn. Gives up after a short run of frames so a button
 * that never lands cannot hold the loading surface open.
 */
@Composable
internal fun ReportSurfaceDraw(
    epoch: Int,
    formReady: Boolean,
    walletShown: Boolean,
    walletReady: Boolean,
    onDrawn: () -> Unit,
) {
    val view = LocalView.current
    val formReadyState = rememberUpdatedState(formReady)
    val walletShownState = rememberUpdatedState(walletShown)
    val walletReadyState = rememberUpdatedState(walletReady)
    val onDrawnState = rememberUpdatedState(onDrawn)
    LaunchedEffect(epoch) {
        if (epoch <= 0) return@LaunchedEffect
        var frames = 0
        while (
            frames < MaxSurfaceFrames &&
            !(formReadyState.value && (!walletShownState.value || walletReadyState.value))
        ) {
            withFrameNanos { }
            frames++
        }
        view.awaitPreDraw()
        onDrawnState.value()
    }
}

internal fun View.armSizedPreDraw(onDrawn: () -> Unit) {
    OneShotPreDrawListener.add(this) {
        if (width > 1 && height > 1) {
            onDrawn()
        } else if (isAttachedToWindow) {
            armSizedPreDraw(onDrawn)
        }
    }
}

private suspend fun View.awaitPreDraw() {
    suspendCancellableCoroutine { continuation ->
        val listener = OneShotPreDrawListener.add(this) {
            if (continuation.isActive) continuation.resume(Unit)
        }
        continuation.invokeOnCancellation { listener.removeListener() }
    }
}

private const val MaxSurfaceFrames = 30
