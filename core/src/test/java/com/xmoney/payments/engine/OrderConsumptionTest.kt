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
        assertTrue(OrderConsumption.shouldConsume(PaymentResult.Status.COMPLETE, false))
        assertTrue(OrderConsumption.shouldConsume(PaymentResult.Status.COMPLETE, true))
        assertTrue(OrderConsumption.shouldConsume(PaymentResult.Status.FAILED, false))
        assertTrue(OrderConsumption.shouldConsume(PaymentResult.Status.FAILED, true))
        assertFalse(OrderConsumption.shouldConsume(PaymentResult.Status.CANCELED, false))
        assertTrue(OrderConsumption.shouldConsume(PaymentResult.Status.CANCELED, true))
    }

    @Test
    fun merchantResultComplete() {
        val tx = Transaction(id = "tx-1", status = "complete")
        val result = PaymentResult(PaymentResult.Status.COMPLETE, tx, null, null)
        val mapped = OrderConsumption.merchantResult(result).getOrThrow()
        assertEquals("tx-1", mapped.id)
    }

    @Test
    fun merchantResultMissingTransaction() {
        val result = PaymentResult(PaymentResult.Status.COMPLETE, null, null, null)
        val error = OrderConsumption.merchantResult(result).exceptionOrNull() as PaymentError
        assertEquals("PAYMENT_ERROR", error.code)
        assertEquals("Missing transaction", error.message)
    }

    @Test
    fun merchantResultFailedIsSanitized() {
        val result = PaymentResult(
            PaymentResult.Status.FAILED,
            null,
            "PAYMENT_ERROR",
            "gateway dump",
        )
        val error = OrderConsumption.merchantResult(result).exceptionOrNull() as PaymentError
        assertEquals(PaymentError.GENERIC_PAYMENT, error.message)
    }

    @Test
    fun merchantResultCanceled() {
        val result = PaymentResult(PaymentResult.Status.CANCELED, null, null, null)
        val error = OrderConsumption.merchantResult(result).exceptionOrNull() as PaymentError
        assertTrue(error is PaymentError.Canceled)
    }
}
