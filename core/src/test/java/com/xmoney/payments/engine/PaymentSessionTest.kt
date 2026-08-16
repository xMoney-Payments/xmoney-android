package com.xmoney.payments.engine

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentSessionTest {
    private lateinit var server: MockWebServer
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
        DigitalWalletFactory.buttonFactory = null
    }

    @After
    fun tearDown() {
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
        DigitalWalletFactory.buttonFactory = null
        server.shutdown()
    }

    @Test
    fun bindCommitsAfterLoad() = runBlocking {
        enqueueToken()
        val session = makeSession()
        val state = session.bind(intent, activity)
        assertEquals("sess-1", state.sessionToken)
        assertEquals("sess-1", session.state?.sessionToken)
        assertFalse(session.isProcessing)
        assertFalse(session.isOrderConsumed)
        assertTrue(session.canDismiss)
    }

    @Test
    fun cancelledBindDoesNotCommit() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS)
                .setBody("""{"token":"sess-1"}"""),
        )
        val session = makeSession()
        val deferred = async {
            try {
                session.bind(intent, activity)
            } catch (_: CancellationException) {
                null
            }
        }
        delay(50)
        deferred.cancel()
        deferred.join()
        assertNull(session.state)
    }

    @Test
    fun submitWhileProcessingIsNoOp() = runBlocking {
        enqueueToken()
        val session = makeSession()
        session.bind(intent, activity)
        val slow = object : DigitalWalletAuthorizing {
            override val didAuthorizePayment: Boolean = false
            override fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {}
            override fun handleResolutionResult(result: ActivityResult) {}
            override suspend fun start(): PaymentResult {
                delay(200)
                return PaymentResult(PaymentResult.Status.CANCELED, null, null, null)
            }
        }
        val first = async { session.startWallet(slow) }
        delay(20)
        val second = session.startWallet(ImmediateWallet(didAuthorizePayment = true, status = PaymentResult.Status.COMPLETE))
        assertEquals(PaymentResult.Status.CANCELED, second.status)
        first.await()
        assertFalse(session.isOrderConsumed)
    }

    @Test
    fun preAuthorizeCancelDoesNotConsume() = runBlocking {
        enqueueToken()
        val session = makeSession()
        session.bind(intent, activity)
        val result = session.startWallet(
            ImmediateWallet(didAuthorizePayment = false, status = PaymentResult.Status.CANCELED),
        )
        assertEquals(PaymentResult.Status.CANCELED, result.status)
        assertFalse(session.isOrderConsumed)
        assertTrue(session.canDismiss)
    }

    @Test
    fun postSubmitCancelConsumes() = runBlocking {
        enqueueToken()
        val session = makeSession()
        session.bind(intent, activity)
        session.startWallet(ImmediateWallet(didAuthorizePayment = true, status = PaymentResult.Status.CANCELED))
        assertTrue(session.isOrderConsumed)
        assertFalse(session.isInteractionEnabled)
    }

    @Test
    fun canDismissIsFalseWhileProcessing() = runBlocking {
        enqueueToken()
        val session = makeSession()
        session.bind(intent, activity)
        val slow = object : DigitalWalletAuthorizing {
            override val didAuthorizePayment: Boolean = true
            override fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {}
            override fun handleResolutionResult(result: ActivityResult) {}
            override suspend fun start(): PaymentResult {
                delay(150)
                return PaymentResult(PaymentResult.Status.COMPLETE, null, null, null)
            }
        }
        val job = async { session.startWallet(slow) }
        delay(20)
        assertFalse(session.canDismiss)
        assertTrue(session.isProcessing)
        job.await()
        assertTrue(session.canDismiss)
    }

    @Test
    fun deleteSavedCardNon2xx_keepsExistingCards() = runBlocking {
        server.dispatcher = pathDispatcher(
            deleteCode = 404,
            cardsBody = """{"data":[{"id":"card-1","cardNumber":"•••• 1111","cardType":"visa","cardExpiryDate":"12/26"}]}""",
        )
        val session = makeSession(savedCardsEnabled = true)
        session.bind(intent, activity)
        try {
            session.deleteSavedCard("card-1")
            org.junit.Assert.fail("expected PaymentError.Network")
        } catch (e: PaymentError.Network) {
            assertTrue(e.message.isNotEmpty())
        }
        assertEquals(listOf("card-1"), session.state?.savedCards?.map { it.id })
    }

    @Test
    fun deleteSavedCard_refreshesRemaining() = runBlocking {
        var remaining =
            """{"data":[{"id":"card-1","cardNumber":"•••• 1111","cardType":"visa","cardExpiryDate":"12/26"},{"id":"card-2","cardNumber":"•••• 5599","cardType":"mastercard","cardExpiryDate":"12/34"}]}"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("session-token") -> MockResponse().setBody("""{"token":"sess-1"}""")
                    request.method == "DELETE" && path.contains("cards") -> {
                        remaining = """{"data":[{"id":"card-2","cardNumber":"•••• 5599","cardType":"mastercard","cardExpiryDate":"12/34"}]}"""
                        MockResponse().setBody("{}")
                    }
                    path.contains("cards") -> MockResponse().setBody(remaining)
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        val session = makeSession(savedCardsEnabled = true)
        session.bind(intent, activity)
        val updated = session.deleteSavedCard("card-1")
        assertEquals(listOf("card-2"), updated.savedCards.map { it.id })
        assertEquals(listOf("card-2"), session.state?.savedCards?.map { it.id })
    }

    private fun pathDispatcher(deleteCode: Int, cardsBody: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            return when {
                path.contains("session-token") -> MockResponse().setBody("""{"token":"sess-1"}""")
                request.method == "DELETE" && path.contains("cards") ->
                    MockResponse().setResponseCode(deleteCode).setBody("""{"message":"not found"}""")
                path.contains("cards") -> MockResponse().setBody(cardsBody)
                else -> MockResponse().setBody("{}")
            }
        }
    }

    private fun enqueueToken() {
        server.enqueue(MockResponse().setBody("""{"token":"sess-1"}"""))
        server.enqueue(MockResponse().setBody("{}"))
    }

    private fun makeSession(savedCardsEnabled: Boolean = false): PaymentSession {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val mapped = req.url.newBuilder()
                    .scheme(server.url("/").scheme)
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(req.newBuilder().url(mapped).build())
            }
            .build()
        return PaymentSession(
            configuration = PaymentConfig(
                publicKey = "pk_test_android",
                card = CardConfig(savedCards = SavedCardsConfig(enabled = savedCardsEnabled)),
            ),
            intent = intent,
            context = activity,
            http = HttpClient(client),
        )
    }

    private val intent = PaymentIntent(OrderPayload("e30="), OrderChecksum("cs"))

    private class ImmediateWallet(
        override val didAuthorizePayment: Boolean,
        private val status: PaymentResult.Status,
    ) : DigitalWalletAuthorizing {
        override fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {}
        override fun handleResolutionResult(result: ActivityResult) {}
        override suspend fun start(): PaymentResult =
            PaymentResult(status, null, null, null)
    }
}
