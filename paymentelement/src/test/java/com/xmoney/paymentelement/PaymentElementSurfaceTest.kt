package com.xmoney.paymentelement

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.network.HttpClient
import com.xmoney.paymentelement.ui.ReportSurfaceDraw
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentElementSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    private val activity: FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    @Test
    fun revealAndReadyFollowTheFirstDraw() {
        val controller = boundController()
        var ready = 0
        compose.setContent {
            PaymentElement(controller, intent("bound")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        compose.waitUntil(5_000) { ready == 1 }
        assertEquals(1, ready)
    }

    @Test
    fun timeoutRevealsWhenTheSurfaceNeverDraws() {
        var drawn = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReportSurfaceDraw(
                epoch = 1,
                formReady = false,
                walletShown = true,
                walletReady = false,
            ) { drawn = true }
        }
        repeat(400) {
            compose.mainClock.advanceTimeByFrame()
        }
        assertTrue(drawn)
    }

    @Test
    fun initialFailureReplacesTheCoinAndEmitsReady() {
        val controller = controller()
        installFailingSession(controller)
        var ready = 0
        compose.setContent {
            PaymentElement(controller, intent("missing")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        compose.waitUntil(5_000) { ready == 1 }
        compose.onNodeWithText(PaymentError.GENERIC_NETWORK).assertExists()
        assertEquals(1, ready)
    }

    @Test
    fun paymentInProgressRetriesWithoutReadyUntilProcessingEnds() {
        val controller = controller()
        installFailingSession(controller)
        setProcessing(controller, true)
        var ready = 0
        compose.setContent {
            PaymentElement(controller, intent("later")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        compose.waitUntil(1_000) { ready == 0 }
        assertEquals(0, ready)

        compose.runOnUiThread {
            setProcessing(controller, false)
            Snapshot.sendApplyNotifications()
        }
        try {
            compose.waitUntil(5_000) { ready == 1 }
        } catch (error: Throwable) {
            fail(
                "ready=$ready processing=${controller.isProcessing} error=${controller.bindError} ${error.message}",
            )
        }
        compose.onNodeWithText(PaymentError.GENERIC_NETWORK).assertExists()
    }

    @Test
    fun remountEmitsReadyAgain() {
        val controller = boundController()
        var ready = 0
        val generation = mutableIntStateOf(0)
        compose.setContent {
            key(generation.intValue) {
                PaymentElement(controller, intent("bound")) {
                    if (it is EmbeddedEvent.Ready) ready++
                }
            }
        }
        compose.waitUntil(5_000) { ready == 1 }
        compose.runOnUiThread {
            generation.intValue = 1
            Snapshot.sendApplyNotifications()
        }
        compose.waitUntil(5_000) { ready == 2 }
        assertEquals(2, ready)
    }

    private fun boundController(): EmbeddedPaymentController {
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
        return controller
    }

    private fun controller() = EmbeddedPaymentController(
        configuration = PaymentConfig(publicKey = "pk_test_x"),
        activity = activity,
        onResult = {},
    )

    private fun installFailingSession(controller: EmbeddedPaymentController) {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("fail")
                .body("""{"message":"nope"}""".toResponseBody())
                .build()
        }.build()
        controller.javaClass.getDeclaredField("session").apply {
            isAccessible = true
            set(
                controller,
                PaymentSession(
                    configuration = PaymentConfig(publicKey = "pk_test_x"),
                    intent = intent("session"),
                    context = activity,
                    http = HttpClient(client),
                ),
            )
        }
    }

    private fun setProcessing(controller: EmbeddedPaymentController, processing: Boolean) {
        controller.javaClass.getDeclaredMethod(
            "setProcessing",
            Boolean::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
            invoke(controller, processing)
        }
    }

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
}
