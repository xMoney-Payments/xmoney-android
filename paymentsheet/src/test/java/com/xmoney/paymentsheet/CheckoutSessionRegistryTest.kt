package com.xmoney.paymentsheet

import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CheckoutSessionRegistryTest {
    @Test
    fun registerGetAndRemove() {
        val id = "req-1"
        val session = CheckoutSessionRegistry.Session(onEvent = {})
        CheckoutSessionRegistry.register(id, session)
        assertSame(session, CheckoutSessionRegistry.get(id))
        CheckoutSessionRegistry.remove(id)
        assertNull(CheckoutSessionRegistry.get(id))
    }

    @Test
    fun concurrentKeysDoNotOverwrite() {
        val a = CheckoutSessionRegistry.Session(onEvent = {})
        val b = CheckoutSessionRegistry.Session(onEvent = {})
        CheckoutSessionRegistry.register("a", a)
        CheckoutSessionRegistry.register("b", b)
        assertSame(a, CheckoutSessionRegistry.get("a"))
        assertSame(b, CheckoutSessionRegistry.get("b"))
        assertNotNull(CheckoutSessionRegistry.get("a"))
        CheckoutSessionRegistry.remove("a")
        CheckoutSessionRegistry.remove("b")
    }
}
