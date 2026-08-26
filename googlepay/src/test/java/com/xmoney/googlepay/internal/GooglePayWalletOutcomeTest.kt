package com.xmoney.googlepay.internal

import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.model.PaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GooglePayWalletOutcomeTest {
    @Test
    fun preAuthCancelDeliversCanceledWithoutConsume() {
        val events = mutableListOf<GooglePayEvent>()
        val results = mutableListOf<PaymentResult>()
        GooglePayWalletOutcome.deliver(
            EngineResult.canceled(),
            events::add,
            results::add,
        )
        assertEquals(listOf(GooglePayEvent.Processing(false)), events)
        assertEquals(listOf(PaymentResult.Canceled), results)
        assertFalse(
            OrderConsumption.shouldConsume(EngineResult.Status.CANCELED, didAuthorize = false),
        )
    }

    @Test
    fun completeStillDeliversAfterProcessingFalse() {
        val events = mutableListOf<GooglePayEvent>()
        val results = mutableListOf<PaymentResult>()
        val complete = EngineResult(
            EngineResult.Status.COMPLETE,
            com.xmoney.payments.model.Transaction(id = "tx-1", status = "complete"),
            null,
            null,
        )
        GooglePayWalletOutcome.deliver(complete, events::add, results::add)
        assertEquals(listOf(GooglePayEvent.Processing(false)), events)
        assertEquals("tx-1", (results.single() as PaymentResult.Complete).transaction.id)
    }
}
