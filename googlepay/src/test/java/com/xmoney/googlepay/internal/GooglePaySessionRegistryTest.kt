package com.xmoney.googlepay.internal

import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePaySessionRegistryTest {
    @Test
    fun registerGetAndRemove() {
        val id = "gp-req-1"
        val session = GooglePaySessionRegistry.Session()
        GooglePaySessionRegistry.register(id, session)
        assertSame(session, GooglePaySessionRegistry.get(id))
        GooglePaySessionRegistry.remove(id)
        assertNull(GooglePaySessionRegistry.get(id))
    }

    @Test
    fun concurrentKeysDoNotOverwrite() {
        val a = GooglePaySessionRegistry.Session()
        val b = GooglePaySessionRegistry.Session()
        GooglePaySessionRegistry.register("a", a)
        GooglePaySessionRegistry.register("b", b)
        assertSame(a, GooglePaySessionRegistry.get("a"))
        assertSame(b, GooglePaySessionRegistry.get("b"))
        assertNotNull(GooglePaySessionRegistry.get("a"))
        GooglePaySessionRegistry.remove("a")
        GooglePaySessionRegistry.remove("b")
    }

    @Test
    fun finishHostDelegatesToCloseTarget() {
        val target = RecordingCloseTarget()
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        GooglePaySessionRegistry.bindCloseTarget("r", target)
        GooglePaySessionRegistry.finishHost("r")
        assertEquals(1, target.calls)
        assertFalse(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun finishHostWithoutCloseTargetMarksClosing() {
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        assertTrue(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.finishHost("r")
        assertFalse(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun closeTargetIgnoresWhileProcessing() {
        val target = RecordingCloseTarget(processing = true)
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        GooglePaySessionRegistry.bindCloseTarget("r", target)
        GooglePaySessionRegistry.finishHost("r")
        assertEquals(0, target.calls)
        assertTrue(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun isActiveAfterRegisterUntilSuccessfulFinish() {
        assertFalse(GooglePaySessionRegistry.isActive(null))
        assertFalse(GooglePaySessionRegistry.isActive(""))
        assertFalse(GooglePaySessionRegistry.isActive("missing"))
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        assertTrue(GooglePaySessionRegistry.isActive("r"))
        val target = RecordingCloseTarget()
        GooglePaySessionRegistry.bindCloseTarget("r", target)
        GooglePaySessionRegistry.finishHost("r")
        assertFalse(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun isActiveWhenCloseRefused() {
        val target = RecordingCloseTarget(processing = true)
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        GooglePaySessionRegistry.bindCloseTarget("r", target)
        GooglePaySessionRegistry.finishHost("r")
        assertTrue(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun bindAfterDismissDuringLaunchCloses() {
        val target = RecordingCloseTarget()
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        GooglePaySessionRegistry.finishHost("r")
        assertEquals(0, target.calls)
        assertFalse(GooglePaySessionRegistry.isActive("r"))
        GooglePaySessionRegistry.bindCloseTarget("r", target)
        assertEquals(1, target.calls)
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun updateOrderDelegatesToOrderTarget() = runBlocking {
        val target = RecordingOrderTarget()
        val intent = dummyIntent("next")
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        GooglePaySessionRegistry.bindOrderTarget("r", target)
        GooglePaySessionRegistry.updateOrder("r", intent)
        assertEquals(1, target.calls)
        assertSame(intent, target.lastIntent)
        GooglePaySessionRegistry.remove("r")
    }

    @Test
    fun updateOrderWithoutHostThrows() {
        val intent = dummyIntent("missing")
        val missing = assertThrows(IllegalStateException::class.java) {
            runBlocking { GooglePaySessionRegistry.updateOrder("missing", intent) }
        }
        assertEquals("Google Pay is not presented", missing.message)

        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        val unbound = assertThrows(IllegalStateException::class.java) {
            runBlocking { GooglePaySessionRegistry.updateOrder("r", intent) }
        }
        assertEquals("Google Pay is not presented", unbound.message)
        GooglePaySessionRegistry.remove("r")

        val blank = assertThrows(IllegalStateException::class.java) {
            runBlocking { GooglePaySessionRegistry.updateOrder(null, intent) }
        }
        assertEquals("Google Pay is not presented", blank.message)
    }

    @Test
    fun updateOrderNoOpWhileProcessing() = runBlocking {
        val target = RecordingOrderTarget(processing = true)
        GooglePaySessionRegistry.register("r", GooglePaySessionRegistry.Session())
        GooglePaySessionRegistry.bindOrderTarget("r", target)
        GooglePaySessionRegistry.updateOrder("r", dummyIntent("busy"))
        assertEquals(0, target.calls)
        GooglePaySessionRegistry.remove("r")
    }

    private fun dummyIntent(suffix: String) = PaymentIntent(
        OrderPayload("payload-$suffix"),
        OrderChecksum("checksum-$suffix"),
    )

    private class RecordingCloseTarget(
        private val processing: Boolean = false,
    ) : GooglePayCloseTarget {
        var calls = 0
        override fun requestClose(): Boolean {
            if (processing) return false
            calls += 1
            return true
        }
    }

    private class RecordingOrderTarget(
        private val processing: Boolean = false,
    ) : GooglePayOrderTarget {
        var calls = 0
        var lastIntent: PaymentIntent? = null
        override suspend fun updateOrder(
            intent: PaymentIntent,
            onEvent: (GooglePayEvent) -> Unit,
        ) {
            if (processing) return
            calls += 1
            lastIntent = intent
        }
    }
}
