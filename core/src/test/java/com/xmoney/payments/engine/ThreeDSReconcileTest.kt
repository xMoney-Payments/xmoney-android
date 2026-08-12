package com.xmoney.payments.engine

import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.Transaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ThreeDSReconcileTest {
    @Test
    fun requireTransactionIdForThreeDS_returnsId() {
        assertEquals("tx-1", requireTransactionIdForThreeDS("tx-1"))
    }

    @Test
    fun requireTransactionIdForThreeDS_throwsWhenMissing() {
        try {
            requireTransactionIdForThreeDS(null)
            fail("expected ThreeDS error")
        } catch (e: PaymentError.ThreeDS) {
            assertEquals("Missing transaction id", e.message)
        }
    }

    @Test
    fun resultFromTransaction_mapsCompleteSuccess() {
        val tx = Transaction(id = "1", status = "complete-ok")
        val result = resultFromTransaction(tx)
        assertEquals(PaymentResult.Status.COMPLETE, result.status)
        assertSame(tx, result.transaction)
        assertEquals(null, result.errorCode)
    }

    @Test
    fun resultFromTransaction_mapsCompleteFail() {
        val tx = Transaction(id = null, status = "complete-failed")
        val result = resultFromTransaction(tx)
        assertEquals(PaymentResult.Status.FAILED, result.status)
        assertEquals("PAYMENT_ERROR", result.errorCode)
    }

    @Test
    fun softCancel_immediateComplete_returnsCompleteWithoutWaiting() = runBlocking {
        val poll = CompletableDeferred<PaymentResult>()
        val result = reconcileCanceledThreeDS(
            fetchTransaction = {
                Transaction(id = "t1", status = "complete")
            },
            pollDeferred = poll,
            graceMs = 5_000L,
        )
        assertEquals(PaymentResult.Status.COMPLETE, result.status)
        assertTrue(poll.isCancelled)
    }

    @Test
    fun softCancel_pendingThenGraceTimeout_returnsCanceled() = runBlocking {
        val poll = async {
            delay(10_000L)
            PaymentResult(PaymentResult.Status.COMPLETE, Transaction(id = null, status = null), null, null)
        }
        val started = System.currentTimeMillis()
        val result = reconcileCanceledThreeDS(
            fetchTransaction = { Transaction(id = null, status = "pending") },
            pollDeferred = poll,
            graceMs = 80L,
        )
        val elapsed = System.currentTimeMillis() - started
        assertEquals(PaymentResult.Status.CANCELED, result.status)
        assertTrue("grace should be short, was ${elapsed}ms", elapsed < 2_000L)
        assertTrue(poll.isCancelled)
    }

    @Test
    fun softCancel_pendingThenPollWinsWithinGrace_returnsPollResult() = runBlocking {
        val complete = PaymentResult(
            status = PaymentResult.Status.COMPLETE,
            transaction = Transaction(id = null, status = "complete"),
            errorCode = null,
            errorMessage = null,
        )
        val poll = async {
            delay(40L)
            complete
        }
        val result = reconcileCanceledThreeDS(
            fetchTransaction = { Transaction(id = null, status = "3d-pending") },
            pollDeferred = poll,
            graceMs = 2_000L,
        )
        assertEquals(PaymentResult.Status.COMPLETE, result.status)
        assertSame(complete, result)
    }

    @Test
    fun softCancel_immediateFetchFails_fallsThroughToGrace() = runBlocking {
        val poll = async {
            delay(30L)
            PaymentResult(
                PaymentResult.Status.FAILED,
                Transaction(id = null, status = "complete-failed"),
                "PAYMENT_ERROR",
                "Transaction complete-failed",
            )
        }
        val result = reconcileCanceledThreeDS(
            fetchTransaction = { error("network") },
            pollDeferred = poll,
            graceMs = 2_000L,
        )
        assertEquals(PaymentResult.Status.FAILED, result.status)
    }
}
