package com.xmoney.paymentelement

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    fun updateOrderDuringPaymentThrowsBeforeCreatingOrder() = runBlocking {
        val controller = controller()
        controller.javaClass.getDeclaredMethod(
            "setProcessing",
            Boolean::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
            invoke(controller, true)
        }
        var createOrderCalled = false

        try {
            controller.updateOrder {
                createOrderCalled = true
                throw AssertionError("createOrder must not run")
            }
            fail("Expected PaymentError.Payment")
        } catch (error: PaymentError.Payment) {
            assertEquals("Payment in progress", error.message)
        }

        assertEquals(false, createOrderCalled)
    }

    @Test
    fun sameOrderDuringPaymentIsNoOp() = runBlocking {
        val controller = controller()
        val order = intent("bound")
        controller.javaClass.getDeclaredField("boundIntent").apply {
            isAccessible = true
            set(controller, order)
        }
        controller.javaClass.getDeclaredMethod("setSheetState", SheetState::class.java).apply {
            isAccessible = true
            invoke(controller, sheetState())
        }
        controller.javaClass.getDeclaredMethod(
            "setProcessing",
            Boolean::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
            invoke(controller, true)
        }

        controller.updateOrder(order)
    }

    @Test
    fun initialBindFailureReportsResultOnce() = runBlocking {
        var results = 0
        val controller = controller { results++ }
        installSession(controller, failingClient())

        try {
            controller.updateOrder(intent("missing"))
            fail("Expected bind to throw")
        } catch (_: Exception) {
        }

        assertEquals(1, results)
        assertEquals(PaymentError.GENERIC_NETWORK, controller.bindError)
    }

    @Test
    fun laterBindFailureKeepsTheOrderAndSkipsResult() = runBlocking {
        var results = 0
        val controller = controller { results++ }
        val bound = intent("bound")
        bind(controller, bound)
        installSession(controller, failingClient())

        try {
            controller.updateOrder(intent("next"))
            fail("Expected bind to throw")
        } catch (_: Exception) {
        }

        assertEquals(0, results)
        assertNull(controller.bindError)
        assertEquals(bound, controller.javaClass.getDeclaredField("boundIntent").apply {
            isAccessible = true
        }.get(controller))
    }

    @Test
    fun sameIntentDuringCreationDoesNotCancelIt() = runBlocking {
        val controller = controller()
        val bound = intent("bound")
        bind(controller, bound)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var created = false

        val creation = async {
            runCatching {
                controller.updateOrder {
                    started.complete(Unit)
                    release.await()
                    created = true
                    bound
                }
            }
        }
        started.await()
        controller.updateOrder(bound)
        assertFalse(creation.isCompleted)

        release.complete(Unit)
        assertNull(creation.await().exceptionOrNull())
        assertTrue(created)
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
                    intent("first")
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
        assertTrue(first.await().exceptionOrNull() is CancellationException)
        assertFalse(controller.isInteractionEnabled)

        releaseSecond.complete(Unit)
        assertTrue(second.await().exceptionOrNull() === ExpectedFailure)
        assertTrue(controller.isInteractionEnabled)
    }

    private fun controller(
        onResult: (PaymentResult) -> Unit = {},
    ): EmbeddedPaymentController =
        EmbeddedPaymentController(
            configuration = PaymentConfig(publicKey = "pk_test_x"),
            activity = activity,
            onResult = onResult,
        )

    private fun intent(suffix: String) = PaymentIntent(
        OrderPayload("payload-$suffix"),
        OrderChecksum("checksum-$suffix"),
    )

    private fun sheetState() = SheetState(
        sessionToken = "session",
        orderInfo = OrderPayloadInfo(
            cardTransactionMode = null,
            isVerifyCard = false,
            amount = 10.0,
            currency = "EUR",
            externalOrderId = null,
            isRecurring = false,
        ),
        savedCards = emptyList(),
        googlePayAvailable = false,
    )

    private fun bind(controller: EmbeddedPaymentController, order: PaymentIntent) {
        controller.javaClass.getDeclaredField("boundIntent").apply {
            isAccessible = true
            set(controller, order)
        }
        controller.javaClass.getDeclaredMethod("setSheetState", SheetState::class.java).apply {
            isAccessible = true
            invoke(controller, sheetState())
        }
    }

    private fun installSession(controller: EmbeddedPaymentController, client: OkHttpClient) {
        val session = PaymentSession(
            configuration = PaymentConfig(publicKey = "pk_test_x"),
            intent = intent("session"),
            context = activity,
            http = HttpClient(client),
        )
        controller.javaClass.getDeclaredField("session").apply {
            isAccessible = true
            set(controller, session)
        }
    }

    private fun failingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("fail")
                .body("""{"message":"nope"}""".toResponseBody())
                .build()
        }
        .build()

    private object ExpectedFailure : RuntimeException()
}
