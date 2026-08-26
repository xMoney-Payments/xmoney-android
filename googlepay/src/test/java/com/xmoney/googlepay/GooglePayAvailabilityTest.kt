package com.xmoney.googlepay

import com.xmoney.googlepay.internal.GooglePaySessionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePayAvailabilityTest {
    @Test
    fun flagsAreIndependent() {
        val availableOnly = GooglePayAvailability(isAvailable = true, isReady = false)
        assertTrue(availableOnly.isAvailable)
        assertFalse(availableOnly.isReady)
    }

    @Test
    fun hostSessionSnapshotsAvailability() {
        val id = "gp-avail-1"
        GooglePaySessionRegistry.register(id, GooglePaySessionRegistry.Session())
        val session = GooglePaySessionRegistry.get(id)!!
        assertFalse(session.isAvailable)
        assertFalse(session.isReady)
        session.isAvailable = true
        session.isReady = true
        assertEquals(true, GooglePaySessionRegistry.get(id)?.isAvailable)
        assertEquals(true, GooglePaySessionRegistry.get(id)?.isReady)
        GooglePaySessionRegistry.remove(id)
    }
}
