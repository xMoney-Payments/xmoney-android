package com.xmoney.payments.engine

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.CustomerDetailsConfig
import com.xmoney.payments.config.CustomerDetailsFields
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.customerdetails.CustomerDetailsInput
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomerDetailsEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun missingDetailsAreRejected() = runBlocking {
        val session = session(card = true, fields = CustomerDetailsFields(email = true))
        session.bind(intent, activity)
        val missing = session.submitNewCard(card, NoThreeDS, null)
        assertEquals(PaymentError.INVALID_CUSTOMER_DETAILS, missing.errorMessage)
    }

    @Test
    fun blankEmailIsRejected() = runBlocking {
        val session = session(card = true, fields = CustomerDetailsFields(email = true))
        session.bind(intent, activity)
        val blank = session.submitNewCard(card, NoThreeDS, CustomerDetailsInput(email = "  "))
        assertEquals(PaymentError.INVALID_CUSTOMER_DETAILS, blank.errorMessage)
    }

    @Test
    fun regionRequiredCountryWithoutRegionIsRejected() = runBlocking {
        val session = session(card = true, fields = CustomerDetailsFields(address = true))
        session.bind(intent, activity)
        val result = session.submitNewCard(
            card,
            NoThreeDS,
            CustomerDetailsInput(
                fullName = "Ada Lovelace",
                country = "US",
                address = "1 Market",
                city = "San Francisco",
                postalCode = "94105",
            ),
        )
        assertEquals(PaymentError.INVALID_CUSTOMER_DETAILS, result.errorMessage)
        assertEquals(EngineResult.Status.FAILED, result.status)
    }

    @Test
    fun fieldsFailureBlocksCardCollectionAndIsIgnoredWhenCardIsOff() = runBlocking {
        val blocked = session(card = true, fields = null, fieldsStatus = 500)
        try {
            blocked.bind(intent, activity)
            error("expected the fields failure to block load")
        } catch (error: PaymentError.Load) {
            assertEquals(PaymentError.GENERIC_LOAD, error.message)
        }

        val open = session(card = false, fields = null, fieldsStatus = 500)
        val state = open.bind(intent, activity)
        assertFalse(state.customerDetailsVisibility.isActive)
    }

    @Test
    fun disabledCollectionIgnoresTheFieldsOverride() = runBlocking {
        val session = session(card = false, fields = CustomerDetailsFields(email = true), fieldsStatus = 500)
        val state = session.bind(intent, activity)
        assertFalse(state.customerDetailsVisibility.isActive)
    }

    private fun session(card: Boolean, fields: CustomerDetailsFields?, fieldsStatus: Int = 200): PaymentSession {
        server.dispatcher = dispatcher(fieldsStatus)
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
                customerDetails = CustomerDetailsConfig(card = card, fields = fields),
            ),
            intent = intent,
            context = activity,
            http = HttpClient(client),
        )
    }

    private fun dispatcher(fieldsStatus: Int) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: ""
            return when {
                path.contains("session-token") -> MockResponse().setBody("""{"token":"sess-1"}""")
                path.contains("inline-checkout/fields") ->
                    if (fieldsStatus == 200) {
                        MockResponse().setBody("""{"data":["email"]}""")
                    } else {
                        MockResponse().setResponseCode(fieldsStatus).setBody("""{"message":"no"}""")
                    }
                else -> MockResponse().setBody("{}")
            }
        }
    }

    private val intent = PaymentIntent(OrderPayload("e30="), OrderChecksum("cs"))
    private val card = CardInput(number = "4242424242424242", expiryMonth = "12", expiryYear = "30", cvv = "123")

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
