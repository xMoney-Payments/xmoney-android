package com.xmoney.payments.engine

import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.Transaction
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal const val THREE_DS_CANCEL_RECONCILE_GRACE_MS: Long = 4_000L

internal fun requireTransactionIdForThreeDS(transactionId: String?): String =
    transactionId ?: throw PaymentError.ThreeDS("Missing transaction id")

internal fun resultFromTransaction(tx: Transaction): PaymentResult {
    val status = tx.status ?: ""
    val succeeded = tx.isSuccessfulComplete
    return PaymentResult(
        status = if (succeeded) PaymentResult.Status.COMPLETE else PaymentResult.Status.FAILED,
        transaction = tx,
        errorCode = if (succeeded) null else "PAYMENT_ERROR",
        errorMessage = if (succeeded) null else "Transaction $status",
    )
}

internal fun isTransactionComplete(tx: Transaction): Boolean = tx.isComplete

internal suspend fun reconcileCanceledThreeDS(
    fetchTransaction: suspend () -> Transaction,
    pollDeferred: Deferred<PaymentResult>,
    graceMs: Long = THREE_DS_CANCEL_RECONCILE_GRACE_MS,
): PaymentResult {
    val immediate = runCatching { fetchTransaction() }.getOrNull()
    if (immediate != null && isTransactionComplete(immediate)) {
        pollDeferred.cancel()
        return resultFromTransaction(immediate)
    }
    return try {
        withTimeout(graceMs) { pollDeferred.await() }
    } catch (_: TimeoutCancellationException) {
        pollDeferred.cancel()
        PaymentResult(PaymentResult.Status.CANCELED, null, null, null)
    }
}
