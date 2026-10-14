package com.xmoney.payments.engine

import android.os.Parcelable
import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize

/** How long a payment recovered after its screen was destroyed is polled before it times out. */
internal const val RECOVERY_POLL_TIMEOUT_MS: Long = 120_000L

/** A submitted payment whose result is not known yet. The session token is scoped to the order. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Parcelize
data class PendingPayment(
    val transactionId: String,
    val sessionToken: String,
) : Parcelable

/**
 * Survives a recreated activity in the same process, for hosts with no saved state of their own
 * (the embedded element). Keyed by the signed order.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object PendingPaymentStore {
    private val pending = mutableMapOf<String, PendingPayment>()

    @Synchronized
    fun put(orderKey: String, payment: PendingPayment?) {
        if (payment == null) pending.remove(orderKey) else pending[orderKey] = payment
    }

    @Synchronized
    fun get(orderKey: String): PendingPayment? = pending[orderKey]

    @Synchronized
    fun clear() = pending.clear()
}
