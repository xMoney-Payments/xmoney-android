package com.xmoney.googlepay.internal

import android.app.Application
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.xmoney.googlepay.GooglePayEvent
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.DigitalWalletFactory
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.network.HttpClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

@RunWith(RobolectricTestRunner::class)
class GooglePayHostActivityTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        GooglePayHostActivity.newSession = null
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
    }

    @Test
    fun initialFailureFinishesWithOneFailedResult() {
        val results = mutableListOf<PaymentResult>()
        val done = CountDownLatch(1)
        val host = launchHost(failingSession()) { result ->
            results += result
            done.countDown()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(host.isFinishing)
        assertEquals(1, results.size)
        assertTrue(results.single() is PaymentResult.Failed)
    }

    @Test
    fun readyFiresOnceAndSameIntentDoesNotCancelCreation() = runBlocking {
        val readyCount = AtomicInteger(0)
        val ready = CountDownLatch(1)
        val host = launchHost(preboundSession(), onEvent = { event ->
            if (event is GooglePayEvent.Ready) {
                readyCount.incrementAndGet()
                ready.countDown()
            }
        })
        assertTrue("Ready was not delivered; finishing=${host.isFinishing}", ready.await(2, TimeUnit.SECONDS))

        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = intent()
        val creation = async {
            runCatching {
                host.updateOrder {
                    started.complete(Unit)
                    release.await()
                    order
                }
            }
        }
        started.await()
        host.updateOrder(order)
        assertFalse(creation.isCompleted)
        assertEquals(1, readyCount.get())

        release.complete(Unit)
        assertTrue(creation.await().isSuccess)
        assertEquals(1, readyCount.get())
        host.finish()
    }

    @Test
    fun newerClosureCancelsThePreviousOne() = runBlocking {
        val ready = CountDownLatch(1)
        val host = launchHost(preboundSession(), onEvent = {
            if (it is GooglePayEvent.Ready) ready.countDown()
        })
        assertTrue("Ready was not delivered; finishing=${host.isFinishing}", ready.await(2, TimeUnit.SECONDS))

        val started = CompletableDeferred<Unit>()
        val first = async {
            runCatching {
                host.updateOrder {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    intent()
                }
            }
        }
        started.await()
        val second = async {
            runCatching {
                host.updateOrder { throw ExpectedFailure }
            }
        }
        assertTrue(first.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertTrue(second.await().exceptionOrNull() === ExpectedFailure)
        host.finish()
    }

    @Test
    fun differentIntentDuringPaymentThrows() = runBlocking {
        val ready = CountDownLatch(1)
        val host = launchHost(preboundSession(), onEvent = {
            if (it is GooglePayEvent.Ready) ready.countDown()
        })
        assertTrue("Ready was not delivered; finishing=${host.isFinishing}", ready.await(2, TimeUnit.SECONDS))
        host.javaClass.getDeclaredField("isProcessing").apply {
            isAccessible = true
            set(host, true)
        }

        try {
            host.updateOrder(intent("other"))
            fail("Expected PaymentError.Payment")
        } catch (error: PaymentError.Payment) {
            assertEquals("Payment in progress", error.message)
        }
        host.finish()
    }

    private fun launchHost(
        session: (PaymentConfig, PaymentIntent, android.content.Context) -> PaymentSession,
        onEvent: (GooglePayEvent) -> Unit = {},
        onResult: (PaymentResult) -> Unit = {},
    ): GooglePayHostActivity {
        val requestId = "host-${System.nanoTime()}"
        val order = intent()
        val config = PaymentConfig(
            publicKey = "pk_test_x",
            paymentMethods = PaymentMethodsConfig(googlePay = GooglePayConfig(enabled = true)),
        ).resolve(order)
        GooglePaySessionRegistry.register(
            requestId,
            GooglePaySessionRegistry.Session(onEvent = onEvent, onResult = onResult, config = config),
        )
        GooglePayHostActivity.newSession = { paymentConfig, paymentIntent, context ->
            DigitalWalletFactory.makeGooglePay = { _, _, _ -> FakeWallet() }
            DigitalWalletFactory.decorateSheetState = { _, state, _ ->
                state.copy(googlePayAllowedPaymentMethods = "[]", googlePayReady = true)
            }
            session(paymentConfig, paymentIntent, context)
        }
        val created = Robolectric.buildActivity(
            GooglePayHostActivity::class.java,
            GooglePayHostActivity.createIntent(app, requestId, config),
        ).setup().get()
        return created
    }

    private fun preboundSession(): (PaymentConfig, PaymentIntent, android.content.Context) -> PaymentSession =
        { config, order, context ->
            val session = PaymentSession(
                configuration = PaymentConfig(
                    publicKey = config.publicKey,
                    card = config.card,
                    paymentMethods = config.paymentMethods,
                    options = config.options,
                ),
                intent = order,
                context = context,
                http = HttpClient(OkHttpClient()),
            )
            val loaded = SheetState(
                sessionToken = "sess-1",
                orderInfo = OrderPayloadInfo(
                    cardTransactionMode = null,
                    isVerifyCard = false,
                    amount = 10.0,
                    currency = "EUR",
                    externalOrderId = null,
                    isRecurring = false,
                ),
                savedCards = emptyList(),
                googlePayAvailable = true,
                googlePayAllowedPaymentMethods = "[]",
                googlePayReady = true,
            )
            session.javaClass.getDeclaredField("state").apply {
                isAccessible = true
                set(session, loaded)
            }
            session.javaClass.getDeclaredField("boundKey").apply {
                isAccessible = true
                set(session, PaymentSession.orderKey(order))
            }
            session
        }

    private fun failingSession(): (PaymentConfig, PaymentIntent, android.content.Context) -> PaymentSession =
        { config, order, context ->
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("fail")
                    .body("""{"message":"nope"}""".toResponseBody())
                    .build()
            }.build()
            PaymentSession(
                configuration = PaymentConfig(
                    publicKey = config.publicKey,
                    paymentMethods = config.paymentMethods,
                ),
                intent = order,
                context = context,
                http = HttpClient(client),
            )
        }

    private fun intent(suffix: String = "order") = PaymentIntent(
        OrderPayload("e30="),
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
