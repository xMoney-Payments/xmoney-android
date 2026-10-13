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
    fun rendererGoneWaitsThirtySecondsForThePoll() {
        assertEquals(
            ThreeDSChallengeFollowUp.WaitThenFail(30_000L, THREE_DS_UNAVAILABLE_MESSAGE),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.RendererGone),
        )
    }

    @Test
    fun hostUnavailableChecksCompletionBeforeFailing() {
        assertEquals(
            ThreeDSChallengeFollowUp.ThrowUnlessComplete(THREE_DS_UNAVAILABLE_MESSAGE),
            threeDSChallengeFollowUp(ThreeDSChallengeEnd.HostUnavailable),
        )
        assertEquals("Unable to present the authentication challenge", THREE_DS_UNAVAILABLE_MESSAGE)
    }
}
