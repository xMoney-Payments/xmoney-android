package com.xmoney.googlepay

import androidx.fragment.app.FragmentActivity
import com.xmoney.googlepay.FakeBackend.Companion.order
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.UpdateOrderSuperseded
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import com.xmoney.payments.engine.DigitalWalletFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GooglePayControllerUpdateOrderTest {
    private val activity: FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    private val backend = FakeBackend()

    @Before
    fun installWithoutPlayServices() {
        GooglePay.register()
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
    }

    @After
    fun tearDown() {
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
    }
    private val events = mutableListOf<GooglePayEvent>()

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

        val returned = controller.updateOrder { order("next") }

        assertEquals(order("next"), returned)
        assertEquals(order("next"), controller.boundIntent)
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
        } catch (_: PaymentError.PaymentInProgress) {
        }

        assertFalse(createOrderCalled)
    }

    @Test
    fun sameIntentDuringPaymentIsNoOp() = runBlocking {
        val controller = controller()
        controller.bind(order("bound"))
        controller.setProcessingForTest(true)

        controller.bind(order("bound"))

        try {
            controller.bind(order("other"))
            fail("Expected PaymentError.PaymentInProgress")
        } catch (_: PaymentError.PaymentInProgress) {
        }
    }

    @Test
    fun supersededCreateOrderThrowsAndLatestCallOwnsInteractionLock() = runBlocking {
        val controller = controller()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        val first = async {
            runCatching {
                controller.updateOrder {
                    firstStarted.complete(Unit)
                    CompletableDeferred<Unit>().await()
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
        assertTrue(first.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertFalse(controller.isInteractionEnabled)

        releaseSecond.complete(Unit)
        assertTrue(second.await().exceptionOrNull() === ExpectedFailure)
        assertTrue(controller.isInteractionEnabled)
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
        assertTrue(events.none { it is GooglePayEvent.UpdateFailed })
    }

    @Test
    fun laterBindFailureLocksTheStaleOrderAndEmitsUpdateFailed() = runBlocking {
        var results = 0
        val controller = controller { results++ }
        controller.bind(order("bound"))
        backend.fail(order("next"))

        try {
            controller.bind(order("next"))
            fail("Expected bind to throw")
        } catch (_: PaymentError) {
        }

        assertEquals(0, results)
        assertNull(controller.bindError)
        assertTrue(controller.isStaleOrder)
        assertFalse(controller.isInteractionEnabled)
        assertEquals(order("bound"), controller.boundIntent)
        assertEquals(1, events.count { it is GooglePayEvent.UpdateFailed })
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
        controller.rebindFromSurface(bound, { events += it }, intentChanged = true)
        assertFalse(creation.isCompleted)
        assertFalse(controller.isInteractionEnabled)

        release.complete(Unit)
        assertEquals(order("next"), creation.await().getOrThrow())
    }

    @Test
    fun surfaceSwitchBackToTheBoundIntentClearsTheStaleLock() = runBlocking {
        val controller = staleController()

        controller.rebindFromSurface(order("bound"), { events += it }, intentChanged = true)

        assertFalse(controller.isStaleOrder)
        assertTrue(controller.isInteractionEnabled)
        assertEquals(order("bound"), controller.boundIntent)
    }

    @Test
    fun surfaceMountWithTheBoundIntentKeepsTheStaleLock() = runBlocking {
        val controller = staleController()

        controller.rebindFromSurface(order("bound"), { events += it }, intentChanged = false)

        assertTrue(controller.isStaleOrder)
        assertFalse(controller.isInteractionEnabled)
    }

    private suspend fun staleController(): GooglePayController {
        val controller = controller()
        controller.bind(order("bound"))
        backend.fail(order("next"))
        try {
            controller.bind(order("next"))
            fail("Expected bind to throw")
        } catch (_: PaymentError) {
        }
        assertTrue(controller.isStaleOrder)
        return controller
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
        controller.updateOrder { bound }
        backend.release(stale)

        assertTrue(first.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertEquals(bound, controller.boundIntent)
        assertTrue(controller.session.isBound(bound))
        assertTrue(controller.isInteractionEnabled)
    }

    private fun controller(
        onResult: (PaymentResult) -> Unit = {},
    ): GooglePayController {
        val configuration = PaymentConfig(
            publicKey = "pk_test_x",
            paymentMethods = PaymentMethodsConfig(googlePay = GooglePayConfig(enabled = true)),
        )
        val controller = GooglePayController(
            configuration = configuration,
            activity = activity,
            onResult = onResult,
        )
        controller.session = backend.session(activity, configuration)
        return controller
    }

    private suspend fun GooglePayController.bind(order: PaymentIntent) =
        updateOrder(order) { events += it }

    private object ExpectedFailure : RuntimeException()
}
