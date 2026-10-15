package com.xmoney.payments.util

import android.os.Looper
import androidx.annotation.RestrictTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Runs [block] on the main thread. A caller already on the main thread keeps its
 * own dispatcher, so a blocked main looper (for example `runBlocking`) cannot deadlock.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
suspend fun <T> onMainThread(block: suspend CoroutineScope.() -> T): T =
    if (Looper.myLooper() == Looper.getMainLooper()) {
        coroutineScope(block)
    } else {
        withContext(Dispatchers.Main.immediate, block)
    }
