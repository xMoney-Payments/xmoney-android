package com.xmoney.payments.model

import kotlinx.coroutines.CancellationException

/**
 * Thrown by `updateOrder` when a newer `updateOrder` call replaced this one.
 *
 * It is a [CancellationException], so structured concurrency treats it as a
 * normal cancellation. `runCatching { }` and `catch (e: Exception)` still catch
 * it; rethrow it or check for it before treating the update as failed.
 */
class UpdateOrderSuperseded : CancellationException("A newer updateOrder call replaced this one")
