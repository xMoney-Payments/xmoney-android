package com.xmoney.payments.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreeDSChallengeFollowUpTest {
    @Test
    fun closedByPollWaits() {
        assertEquals(
            ThreeDSChallengeFollowUp.WaitForPoll,
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.ClosedByPoll),
        )
    }

    @Test
    fun userCloseUsesFourSeconds() {
        assertEquals(
            ThreeDSChallengeFollowUp.ReconcileCancel(THREE_DS_CANCEL_RECONCILE_GRACE_MS),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.UserCanceled),
        )
        assertEquals(4_000L, THREE_DS_CANCEL_RECONCILE_GRACE_MS)
    }

    @Test
    fun bankHandoffCancelUsesThirtySeconds() {
        assertEquals(
            ThreeDSChallengeFollowUp.ReconcileCancel(THREE_DS_BANK_HANDOFF_CANCEL_GRACE_MS),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.BankAppCanceled),
        )
        assertEquals(30_000L, THREE_DS_BANK_HANDOFF_CANCEL_GRACE_MS)
    }

    @Test
    fun rejectedRedirectChecksCompletionBeforeFailing() {
        assertEquals(
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_INSECURE_REDIRECT_MESSAGE),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.RejectedRedirect),
        )
        assertEquals("Insecure authentication redirect", THREE_DS_INSECURE_REDIRECT_MESSAGE)
    }

    @Test
    fun rendererGoneChecksCompletionBeforeFailing() {
        assertEquals(
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_RENDERER_GONE_MESSAGE),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.RendererGone),
        )
        assertEquals("Authentication could not be displayed", THREE_DS_RENDERER_GONE_MESSAGE)
    }

    @Test
    fun loadFailedChecksCompletionBeforeFailing() {
        assertEquals(
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_LOAD_FAILED_MESSAGE),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.LoadFailed),
        )
        assertEquals("Authentication could not be loaded", THREE_DS_LOAD_FAILED_MESSAGE)
    }
}
