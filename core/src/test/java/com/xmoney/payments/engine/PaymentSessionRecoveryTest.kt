package com.xmoney.payments.engine

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.network.HttpClient
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
class PaymentSessionRecoveryTest {
    private lateinit var server: MockWebServer
    private lateinit var activity: FragmentActivity
    private var transactionStatus = "complete-ok"
    private val transactionAuth = mutableListOf<String?>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("session-token") -> MockResponse().setBody("""{"token":"sess-1"}""")
                    path.contains("payment-request") ->
                        MockResponse().setBody("""{"code":200,"data":{"transactionId":"tx-1"}}""")
                    path.contains("transactions/tx-1") -> {
                        transactionAuth += request.getHeader("Authorization")
                        MockResponse().setBody(
                            """{"transactionId":"tx-1","transactionStatus":"$transactionStatus"}""",
                        )
                    }
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        DigitalWalletFactory.makeGooglePay = null
        DigitalWalletFactory.decorateSheetState = null
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun completedSubmitClearsThePendingPayment() = runBlocking {
        val session = makeSession()
        val changes = mutableListOf<PendingPayment?>()
        session.onPendingChanged = { changes += it }
        session.bind(intent, activity)

        val result = session.submitSavedCard("card-1", NoThreeDS)

        assertEquals(EngineResult.Status.COMPLETE, result.status)
        assertEquals(listOf(PendingPayment("tx-1", "sess-1"), null), changes)
        assertNull(session.pendingPayment)
    }

    @Test
    fun cancelledSubmitRethrowsAndKeepsThePendingPayment() = runBlocking {
        transactionStatus = "in-progress"
        val session = makeSession()
        session.bind(intent, activity)

        val submit = async { session.submitSavedCard("card-1", NoThreeDS) }
        var waited = 0
        while (session.pendingPayment == null && waited < 5_000) {
            delay(20)
            waited += 20
        }
        submit.cancel()
        submit.join()

        assertTrue(submit.isCancelled)
        assertEquals(PendingPayment("tx-1", "sess-1"), session.pendingPayment)
        assertFalse(session.isProcessing)
        assertFalse(session.isOrderConsumed)
    }

    @Test
    fun recoverReportsTheResultAndConsumesTheOrder() = runBlocking {
        val session = makeSession()

        val result = session.recover(PendingPayment("tx-1", "sess-restored"))

        assertEquals(EngineResult.Status.COMPLETE, result.status)
        assertEquals("tx-1", result.transaction?.id)
        assertTrue(session.isOrderConsumed)
        assertNull(session.pendingPayment)
        assertEquals("Bearer sess-restored", transactionAuth.first())
    }

    private fun makeSession(): PaymentSession {
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
            configuration = PaymentConfig(publicKey = "pk_test_android"),
            intent = intent,
            context = activity,
            http = HttpClient(client),
        )
    }

    private val intent = PaymentIntent(OrderPayload("e30="), OrderChecksum("cs"))

    private object NoThreeDS : ThreeDSPresenter {
        override suspend fun presentThreeDS(
            url: String,
            formMethod: String,
            params: Map<String, String>,
            onShown: () -> Unit,
        ): ThreeDSChallengeEnd = ThreeDSChallengeEnd.UserCanceled

        override fun dismissThreeDS() {}
    }
}
