package com.xmoney.payments.engine

import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderConsumptionTest {
    @Test
    fun consumeMatrix() {
        assertTrue(OrderConsumption.shouldConsume(EngineResult.Status.COMPLETE, false))
        assertTrue(OrderConsumption.shouldConsume(EngineResult.Status.COMPLETE, true))
        assertTrue(OrderConsumption.shouldConsume(EngineResult.Status.FAILED, false))
        assertTrue(OrderConsumption.shouldConsume(EngineResult.Status.FAILED, true))
        assertFalse(OrderConsumption.shouldConsume(EngineResult.Status.CANCELED, false))
        assertTrue(OrderConsumption.shouldConsume(EngineResult.Status.CANCELED, true))
    }

    @Test
    fun preAuthorizeCancelMapsToCanceledAndDoesNotConsume() {
        assertEquals(PaymentResult.Canceled, OrderConsumption.merchantResult(EngineResult.canceled()))
        assertFalse(OrderConsumption.shouldConsume(EngineResult.Status.CANCELED, false))
    }

    @Test
    fun merchantResultComplete() {
        val tx = Transaction(id = "tx-1", status = "complete")
        val result = EngineResult(EngineResult.Status.COMPLETE, tx, null, null)
        val mapped = OrderConsumption.merchantResult(result) as PaymentResult.Complete
        assertEquals("tx-1", mapped.transaction.id)
    }

    @Test
    fun merchantResultMissingTransaction() {
        val result = EngineResult(EngineResult.Status.COMPLETE, null, null, null)
        val mapped = OrderConsumption.merchantResult(result) as PaymentResult.Failed
        assertEquals("PAYMENT_ERROR", mapped.error.code)
        assertEquals("Missing transaction", mapped.error.message)
    }

    @Test
    fun merchantResultFailedIsSanitized() {
        val result = EngineResult(
            EngineResult.Status.FAILED,
            null,
            "PAYMENT_ERROR",
            "gateway dump",
        )
        val mapped = OrderConsumption.merchantResult(result) as PaymentResult.Failed
        assertEquals(PaymentError.GENERIC_PAYMENT, mapped.error.message)
    }

    @Test
    fun merchantResultCanceled() {
        val result = EngineResult.canceled()
        assertEquals(PaymentResult.Canceled, OrderConsumption.merchantResult(result))
    }

    @Test
    fun merchantResultFailedCanceledCodeMapsToCanceled() {
        val result = EngineResult(
            EngineResult.Status.FAILED,
            null,
            "CANCELED",
            "Payment canceled",
        )
        assertEquals(PaymentResult.Canceled, OrderConsumption.merchantResult(result))
    }
}
