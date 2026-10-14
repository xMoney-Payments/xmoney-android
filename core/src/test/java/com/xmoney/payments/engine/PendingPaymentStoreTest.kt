package com.xmoney.payments.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingPaymentStoreTest {
    @After
    fun tearDown() = PendingPaymentStore.clear()

    @Test
    fun keepsOnePaymentPerOrder() {
        val first = PendingPayment("tx-1", "sess-1")
        val second = PendingPayment("tx-2", "sess-2")
        PendingPaymentStore.put("order-a", first)
        PendingPaymentStore.put("order-b", second)
        assertEquals(first, PendingPaymentStore.get("order-a"))
        assertEquals(second, PendingPaymentStore.get("order-b"))
    }

    @Test
    fun nullClearsTheOrder() {
        PendingPaymentStore.put("order-a", PendingPayment("tx-1", "sess-1"))
        PendingPaymentStore.put("order-a", null)
        assertNull(PendingPaymentStore.get("order-a"))
    }
}
