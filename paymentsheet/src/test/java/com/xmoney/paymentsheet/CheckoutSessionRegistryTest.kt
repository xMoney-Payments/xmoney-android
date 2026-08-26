package com.xmoney.paymentsheet

import com.xmoney.paymentsheet.internal.CheckoutCloseTarget
import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry

import org.junit.Assert.assertEquals
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

    @Test
    fun finishHostDelegatesToCloseTarget() {
        val target = RecordingCloseTarget()
        CheckoutSessionRegistry.register("r", CheckoutSessionRegistry.Session())
        CheckoutSessionRegistry.bindCloseTarget("r", target)
        CheckoutSessionRegistry.finishHost("r")
        assertEquals(1, target.calls)
        CheckoutSessionRegistry.remove("r")
    }

    @Test
    fun finishHostWithMissingCloseTargetIsNoOp() {
        CheckoutSessionRegistry.register("r", CheckoutSessionRegistry.Session())
        CheckoutSessionRegistry.finishHost("r")
        CheckoutSessionRegistry.remove("r")
    }

    @Test
    fun closeTargetIgnoresWhileProcessing() {
        val target = RecordingCloseTarget(processing = true)
        CheckoutSessionRegistry.register("r", CheckoutSessionRegistry.Session())
        CheckoutSessionRegistry.bindCloseTarget("r", target)
        CheckoutSessionRegistry.finishHost("r")
        assertEquals(0, target.calls)
        CheckoutSessionRegistry.remove("r")
    }

    private class RecordingCloseTarget(
        private val processing: Boolean = false,
    ) : CheckoutCloseTarget {
        var calls = 0
        override fun requestClose() {
            if (processing) return
            calls += 1
        }
    }
}
