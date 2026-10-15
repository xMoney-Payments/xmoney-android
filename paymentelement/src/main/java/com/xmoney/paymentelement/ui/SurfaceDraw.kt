package com.xmoney.paymentelement.ui

import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import androidx.core.view.OneShotPreDrawListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits until the card form has a real size and, when Google Pay is shown, the
 * wallet button has pre-drawn. Gives up after two seconds so a button that
 * never lands cannot hold the loading surface open.
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
        val deadline = SystemClock.elapsedRealtime() + SurfaceDrawTimeoutMillis
        val contentReady = withTimeoutOrNull(SurfaceDrawTimeoutMillis) {
            while (!(formReadyState.value && (!walletShownState.value || walletReadyState.value))) {
                withFrameNanos { }
            }
            true
        } == true
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val preDrawn = remaining > 0L && withTimeoutOrNull(remaining) {
            view.awaitPreDraw()
            true
        } == true
        if (!contentReady || !preDrawn) {
            Log.d(SurfaceDrawLogTag, "Checkout draw timed out; revealing fallback")
        }
        onDrawnState.value()
    }
}

/**
 * Calls [onDrawn] once this view pre-draws with a real size, or after the draw
 * timeout. Returns a handle that cancels both the listener and the timer.
 */
internal fun View.armSizedPreDraw(onDrawn: () -> Unit): () -> Unit {
    val completed = AtomicBoolean(false)
    val listener = arrayOfNulls<OneShotPreDrawListener>(1)
    val timeout = Runnable {
        if (completed.compareAndSet(false, true)) {
            Log.d(SurfaceDrawLogTag, "Wallet draw timed out; revealing fallback")
            onDrawn()
        }
    }
    postDelayed(timeout, SurfaceDrawTimeoutMillis)
    armSizedPreDraw(completed, listener) {
        removeCallbacks(timeout)
        onDrawn()
    }
    return {
        completed.set(true)
        removeCallbacks(timeout)
        listener[0]?.removeListener()
    }
}

private fun View.armSizedPreDraw(
    completed: AtomicBoolean,
    listener: Array<OneShotPreDrawListener?>,
    onDrawn: () -> Unit,
) {
    listener[0] = OneShotPreDrawListener.add(this) {
        if (completed.get()) return@add
        if (width > 1 && height > 1) {
            if (completed.compareAndSet(false, true)) onDrawn()
        } else if (isAttachedToWindow) {
            armSizedPreDraw(completed, listener, onDrawn)
        }
    }
}

internal suspend fun View.awaitPreDraw() {
    suspendCancellableCoroutine { continuation ->
        val listener = OneShotPreDrawListener.add(this) {
            if (continuation.isActive) continuation.resume(Unit)
        }
        continuation.invokeOnCancellation { listener.removeListener() }
    }
}

internal suspend fun View.awaitPreDrawOrTimeout() {
    val drew = withTimeoutOrNull(SurfaceDrawTimeoutMillis) {
        awaitPreDraw()
        true
    } == true
    if (!drew) {
        Log.d(SurfaceDrawLogTag, "Checkout draw timed out; revealing fallback")
    }
}

private const val SurfaceDrawTimeoutMillis = 2_000L
private const val SurfaceDrawLogTag = "XMoneySurfaceDraw"
