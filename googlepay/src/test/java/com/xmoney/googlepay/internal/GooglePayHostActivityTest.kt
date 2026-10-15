package com.xmoney.googlepay.internal

import android.app.Application
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.xmoney.googlepay.FakeBackend
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.DigitalWalletFactory
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.UpdateOrderSuperseded
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class GooglePayHostActivityTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val backend = FakeBackend()
    private val events = mutableListOf<GooglePayEvent>()
    private val results = mutableListOf<PaymentResult>()

    @After
    fun tearDown() {
        GooglePayHostActivity.sessionFactory = null
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
    }

    @Test
    fun initialFailureFinishesWithOneFailedResultAndNoReady() {
        backend.failAll = true
        val host = launchHost().get()

        pumpUntil { results.isNotEmpty() }
        assertTrue(host.isFinishing)
        assertEquals(1, results.size)
        assertTrue(results.single() is PaymentResult.Failed)
        assertTrue(events.none { it == GooglePayEvent.Ready })
    }

    @Test
    fun readyFiresOnceAndMerchantCallWithTheBoundIntentSupersedesCreation() = runBlocking {
        val host = launchReadyHost()

        val started = CompletableDeferred<Unit>()
        val creation = async {
            runCatching {
                host.updateOrder(null) {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    intent("next")
                }
            }
        }
        started.await()
        host.updateOrder(intent(), null)

        assertTrue(creation.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertEquals(1, events.count { it == GooglePayEvent.Ready })
        host.finish()
    }

    @Test
    fun createOrderReturnsTheBoundIntentWithoutASecondReady() = runBlocking {
        val host = launchReadyHost()

        val returned = host.updateOrder(null) { intent("next") }

        assertEquals(intent("next"), returned)
        assertEquals(1, events.count { it == GooglePayEvent.Ready })
        host.finish()
    }

    @Test
    fun newerClosureSupersedesThePreviousOne() = runBlocking {
        val host = launchReadyHost()

        val started = CompletableDeferred<Unit>()
        val first = async {
            runCatching {
                host.updateOrder(null) {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    intent("first")
                }
            }
        }
        started.await()
        val second = async {
            runCatching {
                host.updateOrder(null) { throw ExpectedFailure }
            }
        }
        assertTrue(first.await().exceptionOrNull() is UpdateOrderSuperseded)
        assertTrue(second.await().exceptionOrNull() === ExpectedFailure)
        host.finish()
    }

    @Test
    fun laterBindFailureLocksTheStaleOrderAndEmitsUpdateFailed() = runBlocking {
        val host = launchReadyHost()
        backend.fail(intent("next"))

        try {
            host.updateOrder(intent("next"), null)
            fail("Expected bind to throw")
        } catch (_: PaymentError) {
        }

        assertTrue(host.isStaleOrder)
        assertFalse(host.isFinishing)
        assertTrue(results.isEmpty())
        assertEquals(1, events.count { it is GooglePayEvent.UpdateFailed })

        backend.succeed(intent("next"))
        host.updateOrder(intent("next"), null)
        assertFalse(host.isStaleOrder)
        assertEquals(1, events.count { it == GooglePayEvent.Ready })
        host.finish()
    }

    @Test
    fun differentIntentDuringPaymentThrowsPaymentInProgress() = runBlocking {
        val host = launchReadyHost()
        host.setProcessingForTest(true)

        try {
            host.updateOrder(intent("other"), null)
            fail("Expected PaymentError.PaymentInProgress")
        } catch (_: PaymentError.PaymentInProgress) {
        }
        host.updateOrder(intent(), null)
        host.setProcessingForTest(false)
        host.finish()
    }

    @Test
    fun recreatedHostDoesNotEmitReadyAgain() {
        val controller = launchHost()
        pumpUntil { events.contains(GooglePayEvent.Ready) }

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(controller.get().isFinishing)
        assertEquals(1, events.count { it == GooglePayEvent.Ready })
        controller.get().finish()
    }

    private fun launchReadyHost(): GooglePayHostActivity {
        val host = launchHost().get()
        pumpUntil { events.contains(GooglePayEvent.Ready) || host.isFinishing }
        assertFalse("host finished: $results", host.isFinishing)
        return host
    }

    private fun launchHost(): ActivityController<GooglePayHostActivity> {
        val requestId = "host-${System.nanoTime()}"
        val order = intent()
        val config = PaymentConfig(
            publicKey = "pk_test_x",
            paymentMethods = PaymentMethodsConfig(googlePay = GooglePayConfig(enabled = true)),
        ).resolve(order)
        GooglePaySessionRegistry.register(
            requestId,
            GooglePaySessionRegistry.Session(
                onEvent = { events += it },
                onResult = { results += it },
                config = config,
            ),
        )
        GooglePayHostActivity.sessionFactory = { paymentConfig, paymentIntent, context ->
            DigitalWalletFactory.makeGooglePay = { _, _, _ -> FakeWallet() }
            DigitalWalletFactory.decorateSheetState = { _, state, _ ->
                state.copy(googlePayAllowedPaymentMethods = "[]", googlePayReady = true)
            }
            backend.session(context, paymentConfig, paymentIntent)
        }
        return Robolectric.buildActivity(
            GooglePayHostActivity::class.java,
            GooglePayHostActivity.createIntent(app, requestId, config),
        ).setup()
    }

    /** Runs main-looper work until [condition] holds; network loads finish on IO threads meanwhile. */
    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met; events=$events results=$results" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun intent(suffix: String = "order") = PaymentIntent(
        OrderPayload("e30=$suffix"),
        OrderChecksum("checksum-$suffix"),
    )

    private class FakeWallet : DigitalWalletAuthorizing {
        override val didAuthorizePayment: Boolean = false
        override fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit
        override fun handleResolutionResult(result: ActivityResult) = Unit
        override suspend fun start(): EngineResult = EngineResult.canceled()
    }

    private object ExpectedFailure : RuntimeException()
}
