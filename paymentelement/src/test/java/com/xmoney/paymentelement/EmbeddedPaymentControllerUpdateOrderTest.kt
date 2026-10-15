package com.xmoney.paymentelement

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.UpdateOrderSuperseded
import com.xmoney.paymentelement.FakeBackend.Companion.order
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedPaymentControllerUpdateOrderTest {
    private val activity: FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    private val backend = FakeBackend()
    private val events = mutableListOf<EmbeddedEvent>()

    @Test
    fun createOrderFailureRestoresInteractionAndDoesNotCallOnResult() = runBlocking {
        var resultCount = 0
        val controller = controller { resultCount++ }

        try {
            controller.updateOrder {
                throw ExpectedFailure
            }
            fail("Expected createOrder to throw")
        } catch (error: Throwable) {
            assertTrue(error === ExpectedFailure)
        }

        assertTrue(controller.isInteractionEnabled)
        assertEquals(0, resultCount)
    }

    @Test
    fun createOrderReturnsTheBoundIntent() = runBlocking {
        val controller = controller()
        val next = order("next")

        val returned = controller.updateOrder { next }

        assertEquals(next, returned)
        assertEquals(next, controller.boundIntent)
        assertTrue(controller.isInteractionEnabled)
    }

    @Test
    fun updateOrderDuringPaymentThrowsBeforeCreatingOrder() = runBlocking {
        val controller = controller()
        controller.setProcessingForTest(true)
        var createOrderCalled = false

        try {
            controller.updateOrder {
                createOrderCalled = true
                throw AssertionError("createOrder must not run")
            }
            fail("Expected PaymentError.PaymentInProgress")
        } catch (error: PaymentError.PaymentInProgress) {
            assertEquals(PaymentError.PAYMENT_IN_PROGRESS, error.message)
        }

        assertFalse(createOrderCalled)
    }

    @Test
    fun sameOrderDuringPaymentIsNoOp() = runBlocking {
        val controller = controller()
        val bound = order("bound")
        controller.bind(bound)
        controller.setProcessingForTest(true)

        controller.bind(bound)

        assertEquals(bound, controller.boundIntent)
    }

    @Test
    fun differentOrderDuringPaymentThrowsPaymentInProgress() = runBlocking {
        val controller = controller()
        controller.bind(order("bound"))
        controller.setProcessingForTest(true)

        try {
            controller.bind(order("next"))
            fail("Expected PaymentError.PaymentInProgress")
        } catch (_: PaymentError.PaymentInProgress) {
        }
        assertEquals(order("bound"), controller.boundIntent)
    }

    @Test
    fun initialBindFailureReportsResultOncePerStreak() = runBlocking {
        var results = 0
        val controller = controller { results++ }
        backend.failAll = true

        repeat(2) {
            try {
                controller.bind(order("missing"))
                fail("Expected bind to throw")
            } catch (_: PaymentError) {
            }
        }

        assertEquals(1, results)
        assertEquals(PaymentError.GENERIC_NETWORK, controller.bindError)
        assertTrue(events.none { it is EmbeddedEvent.UpdateFailed })

        backend.failAll = false
        controller.bind(order("missing"))
        assertNull(controller.bindError)
        assertTrue(controller.isInteractionEnabled)
    }

    @Test
    fun laterBindFailureLocksTheStaleOrderAndEmitsUpdateFailed() = runBlocking {
        var results = 0
        val controller = controller { results++ }
        val bound = order("bound")
        val next = order("next")
        controller.bind(bound)
        backend.fail(next)

        try {
            controller.bind(next)
            fail("Expected bind to throw")
        } catch (_: PaymentError) {
        }

        assertEquals(0, results)
        assertNull(controller.bindError)
        assertEquals(bound, controller.boundIntent)
        assertTrue(controller.isStaleOrder)
        assertFalse(controller.isInteractionEnabled)
        assertEquals(1, events.count { it is EmbeddedEvent.UpdateFailed })

        backend.succeed(next)
        controller.bind(next)
        assertFalse(controller.isStaleOrder)
        assertTrue(controller.isInteractionEnabled)
        assertEquals(next, controller.boundIntent)
    }

    @Test
    fun createOrderFailureAfterBindKeepsThePreviousOrderPayable() = runBlocking {
        val controller = controller()
        val bound = order("bound")
        controller.bind(bound)

        try {
            controller.updateOrder { throw ExpectedFailure }
            fail("Expected createOrder to throw")
        } catch (error: Throwable) {
            assertTrue(error === ExpectedFailure)
        }

        assertEquals(bound, controller.boundIntent)
        assertTrue(controller.isInteractionEnabled)
        assertTrue(events.none { it is EmbeddedEvent.UpdateFailed })
    }

    @Test
    fun surfaceRebindOfTheBoundIntentDoesNotCancelCreation() = runBlocking {
        val controller = controller()
        val bound = order("bound")
        controller.bind(bound)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val creation = async {
            runCatching {
                controller.updateOrder {
                    started.complete(Unit)
                    release.await()
                    order("next")
                }
            }
        }
        started.await()
        controller.rebindFromSurface(bound) { events += it }
        assertFalse(creation.isCompleted)

        release.complete(Unit)
        assertEquals(order("next"), creation.await().getOrThrow())
    }

    @Test
    fun merchantCallWithTheBoundIntentSupersedesCreation() = runBlocking {
        val controller = controller()
        val bound = order("bound")
        controller.bind(bound)
        val started = CompletableDeferred<Unit>()

        val creation = async {
            runCatching {
                controller.updateOrder {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    order("next")
                }
            }
        }
        started.await()
        controller.bind(bound)

        assertTrue(creation.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertEquals(bound, controller.boundIntent)
        assertTrue(controller.isInteractionEnabled)
    }

    @Test
    fun supersededCreateOrderStaysLockedUntilLatestCallSettles() = runBlocking {
        val controller = controller()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        val first = async {
            runCatching {
                controller.updateOrder {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    order("first")
                }
            }
        }
        firstStarted.await()
        assertFalse(controller.isInteractionEnabled)

        val second = async {
            runCatching {
                controller.updateOrder {
                    secondStarted.complete(Unit)
                    releaseSecond.await()
                    throw ExpectedFailure
                }
            }
        }
        secondStarted.await()
        releaseFirst.complete(Unit)
        assertTrue(first.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertFalse(controller.isInteractionEnabled)

        releaseSecond.complete(Unit)
        assertTrue(second.await().exceptionOrNull() === ExpectedFailure)
        assertTrue(controller.isInteractionEnabled)
    }

    @Test
    fun supersededLoadNeverReplacesTheBoundOrder() = runBlocking {
        val controller = controller()
        val bound = order("bound")
        val stale = order("stale")
        controller.bind(bound)
        backend.hold(stale)

        val first = async { runCatching { controller.updateOrder { stale } } }
        backend.awaitLoading(stale)
        val second = controller.updateOrder { bound }
        backend.release(stale)

        assertTrue(first.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertEquals(bound, second)
        assertEquals(bound, controller.boundIntent)
        assertTrue(controller.session.isBound(bound))
        assertFalse(controller.session.isBound(stale))
        assertTrue(controller.isInteractionEnabled)
    }

    @Test
    fun supersededLoadNeverReplacesTheOrderWhenTheNewerCreationFails() = runBlocking {
        val controller = controller()
        val bound = order("bound")
        val stale = order("stale")
        controller.bind(bound)
        backend.hold(stale)

        val first = async { runCatching { controller.updateOrder { stale } } }
        backend.awaitLoading(stale)
        val second = runCatching { controller.updateOrder { throw ExpectedFailure } }
        backend.release(stale)

        assertTrue(first.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertTrue(second.exceptionOrNull() === ExpectedFailure)
        assertEquals(bound, controller.boundIntent)
        assertTrue(controller.session.isBound(bound))
        assertFalse(controller.session.isBound(stale))
        assertTrue(controller.isInteractionEnabled)
    }

    private fun controller(
        onResult: (PaymentResult) -> Unit = {},
    ): EmbeddedPaymentController {
        val controller = EmbeddedPaymentController(
            configuration = PaymentConfig(publicKey = "pk_test_x"),
            activity = activity,
            onResult = onResult,
        )
        controller.session = backend.session(activity)
        return controller
    }

    private suspend fun EmbeddedPaymentController.bind(order: PaymentIntent) =
        updateOrder(order) { events += it }

    private object ExpectedFailure : RuntimeException()
}
