package com.xmoney.googlepay.internal

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
}
