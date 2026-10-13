package com.xmoney.payments.engine

import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.Transaction
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal const val THREE_DS_CANCEL_RECONCILE_GRACE_MS: Long = 4_000L

/** After a close once the shopper went to the bank app, or after a renderer crash. Long enough for an approval that just landed. */
internal const val THREE_DS_BANK_HANDOFF_CANCEL_GRACE_MS: Long = 30_000L

internal const val THREE_DS_INSECURE_REDIRECT_MESSAGE: String = "Insecure authentication redirect"

/** Same text as iOS `.unavailable`. */
internal const val THREE_DS_UNAVAILABLE_MESSAGE: String = "Unable to present the authentication challenge"

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
enum class ThreeDSChallengeEnd {
    ClosedByPoll,
    UserCanceled,
    /** Closed after a bank-app hand-off, or after the app went to the background during the challenge. */
    BankAppCanceled,
    RejectedRedirect,
    /** The WebView renderer crashed. The bank may still approve, so the poll gets a grace period. */
    RendererGone,
    /** The activity was finishing or destroyed, so the challenge could not stay on screen. Checked once before failing. */
    HostUnavailable,
}

internal sealed class ThreeDSChallengeFollowUp {
    data object WaitForPoll : ThreeDSChallengeFollowUp()
    data class ReconcileCancel(val graceMs: Long) : ThreeDSChallengeFollowUp()
    data class ThrowUnlessComplete(val message: String) : ThreeDSChallengeFollowUp()
    data class WaitThenFail(val graceMs: Long, val message: String) : ThreeDSChallengeFollowUp()
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
        ThreeDSChallengeEnd.HostUnavailable ->
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_UNAVAILABLE_MESSAGE)
        ThreeDSChallengeEnd.RendererGone ->
            ThreeDSChallengeFollowUp.WaitThenFail(THREE_DS_BANK_HANDOFF_CANCEL_GRACE_MS, THREE_DS_UNAVAILABLE_MESSAGE)
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

/** Like [reconcileCanceledThreeDS], but the shopper did not cancel, so running out of time is a 3DS error. */
internal suspend fun waitForPollOrFail(
    fetchTransaction: suspend () -> Transaction,
    pollDeferred: Deferred<EngineResult>,
    graceMs: Long,
    message: String,
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
        throw PaymentError.ThreeDS(message)
    }
}
