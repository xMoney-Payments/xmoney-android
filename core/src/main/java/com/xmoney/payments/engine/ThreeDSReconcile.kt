package com.xmoney.payments.engine

import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.Transaction
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal const val THREE_DS_CANCEL_RECONCILE_GRACE_MS: Long = 4_000L

/** After the shopper cancels the bank-app waiting screen. Long enough for an approval that just landed. */
internal const val THREE_DS_BANK_HANDOFF_CANCEL_GRACE_MS: Long = 30_000L

internal const val THREE_DS_INSECURE_REDIRECT_MESSAGE: String = "Insecure authentication redirect"

internal const val THREE_DS_RENDERER_GONE_MESSAGE: String = "Authentication could not be displayed"

internal const val THREE_DS_LOAD_FAILED_MESSAGE: String = "Authentication could not be loaded"

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
enum class ThreeDSChallengeEnd {
    ClosedByPoll,
    UserCanceled,
    BankAppCanceled,
    RejectedRedirect,
    RendererGone,
    LoadFailed,
}

internal sealed class ThreeDSChallengeFollowUp {
    data object WaitForPoll : ThreeDSChallengeFollowUp()
    data class ReconcileCancel(val graceMs: Long) : ThreeDSChallengeFollowUp()
    data class ThrowUnlessComplete(val message: String) : ThreeDSChallengeFollowUp()
}

internal fun threeDSChallengeFollowUp(end: ThreeDSChallengeEnd): ThreeDSChallengeFollowUp =
    when (end) {
        ThreeDSChallengeEnd.ClosedByPoll -> ThreeDSChallengeFollowUp.WaitForPoll
        ThreeDSChallengeEnd.UserCanceled ->
            ThreeDSChallengeFollowUp.ReconcileCancel(THREE_DS_CANCEL_RECONCILE_GRACE_MS)
        ThreeDSChallengeEnd.BankAppCanceled ->
            ThreeDSChallengeFollowUp.ReconcileCancel(THREE_DS_BANK_HANDOFF_CANCEL_GRACE_MS)
        ThreeDSChallengeEnd.RejectedRedirect ->
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_INSECURE_REDIRECT_MESSAGE)
        ThreeDSChallengeEnd.RendererGone ->
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_RENDERER_GONE_MESSAGE)
        ThreeDSChallengeEnd.LoadFailed ->
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_LOAD_FAILED_MESSAGE)
    }

internal fun requireTransactionIdForThreeDS(transactionId: String?): String =
    transactionId ?: throw PaymentError.ThreeDS("Missing transaction id")

internal fun resultFromTransaction(tx: Transaction): EngineResult {
    val status = tx.status ?: ""
    val succeeded = tx.isSuccessfulComplete
    return EngineResult(
        status = if (succeeded) EngineResult.Status.COMPLETE else EngineResult.Status.FAILED,
        transaction = tx,
        errorCode = if (succeeded) null else "PAYMENT_ERROR",
        errorMessage = if (succeeded) null else "Transaction $status",
    )
}

internal fun isTransactionComplete(tx: Transaction): Boolean = tx.isComplete

internal suspend fun reconcileCanceledThreeDS(
    fetchTransaction: suspend () -> Transaction,
    pollDeferred: Deferred<EngineResult>,
    graceMs: Long = THREE_DS_CANCEL_RECONCILE_GRACE_MS,
): EngineResult {
    val immediate = runCatching { fetchTransaction() }.getOrNull()
    if (immediate != null && isTransactionComplete(immediate)) {
        pollDeferred.cancel()
        return resultFromTransaction(immediate)
    }
    return try {
        withTimeout(graceMs) { pollDeferred.await() }
    } catch (_: TimeoutCancellationException) {
        pollDeferred.cancel()
        EngineResult(EngineResult.Status.CANCELED, null, null, null)
    }
}
