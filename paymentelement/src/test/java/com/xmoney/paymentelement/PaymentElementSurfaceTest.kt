package com.xmoney.paymentelement

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentelement.FakeBackend.Companion.order
import com.xmoney.paymentelement.ui.ReportSurfaceDraw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaymentElementSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    private val activity: FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    private val backend = FakeBackend()
    private lateinit var merchant: CoroutineScope
    private val failureText = Strings.text("sheet.loadFailed", "en")

    @Test
    fun revealAndReadyFollowTheFirstDraw() {
        val controller = controller()
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("bound")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        waitFor(controller, "ready == 1") { ready == 1 }
        settle()
        assertEquals(1, ready)
    }

    @Test
    fun loadingCoverHasALocalizedLabel() {
        val controller = controller()
        backend.hold(order("bound"))
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("bound"))
        }
        compose.onNodeWithContentDescription(Strings.text("sheet.loading", "en")).assertExists()
        backend.release(order("bound"))
    }

    @Test
    fun timeoutRevealsWhenTheSurfaceNeverDraws() {
        var drawn = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            merchant = rememberCoroutineScope()
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
    fun initialFailureShowsTheFailureStateWithoutReady() {
        var results = 0
        val controller = controller { results++ }
        backend.failAll = true
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("missing")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        waitFor(controller, "failure text shown") {
            compose.onAllNodesWithText(failureText).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(controller.bindError != null)
        settle()
        assertEquals(0, ready)
        assertEquals(1, results)
    }

    @Test
    fun retryAfterInitialFailureEmitsReady() {
        val controller = controller()
        backend.failAll = true
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("missing")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        waitFor(controller, "controller.bindError != null") { controller.bindError != null }
        assertEquals(0, ready)

        backend.failAll = false
        val retry = merchantUpdate(controller, order("missing"))
        waitFor(controller, "ready == 1") { ready == 1 }
        compose.onNodeWithText(failureText).assertDoesNotExist()
        assertTrue(retry.isCompleted && retry.getCompleted().isSuccess)
    }

    @Test
    fun laterBindFailureEmitsUpdateFailedAndKeepsPayLocked() {
        val controller = controller()
        val intent = mutableStateOf(order("bound"))
        val events = mutableListOf<EmbeddedEvent>()
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, intent.value) { events += it }
        }
        waitFor(controller, "events.contains(EmbeddedEvent.Ready)") { events.contains(EmbeddedEvent.Ready) }

        backend.fail(order("next"))
        setIntent(intent, order("next"))
        waitFor(controller, "events.any { it is EmbeddedEvent.UpdateFailed }") { events.any { it is EmbeddedEvent.UpdateFailed } }
        assertTrue(controller.isStaleOrder)
        assertTrue(!controller.isInteractionEnabled)
        assertEquals(order("bound"), controller.boundIntent)
        assertEquals(1, events.count { it == EmbeddedEvent.Ready })
    }

    @Test
    fun switchingBackToTheBoundIntentClearsTheStaleLock() {
        val controller = controller()
        val intent = mutableStateOf(order("bound"))
        val events = mutableListOf<EmbeddedEvent>()
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, intent.value) { events += it }
        }
        waitFor(controller, "ready") { events.contains(EmbeddedEvent.Ready) }
        backend.fail(order("next"))
        setIntent(intent, order("next"))
        waitFor(controller, "stale") { controller.isStaleOrder }

        setIntent(intent, order("bound"))
        waitFor(controller, "stale lock cleared") { !controller.isStaleOrder }
        assertTrue(controller.isInteractionEnabled)
        assertEquals(order("bound"), controller.boundIntent)
    }

    @Test
    fun remountWithTheBoundIntentKeepsTheStaleLock() {
        val controller = controller()
        val mount = mutableIntStateOf(0)
        val events = mutableListOf<EmbeddedEvent>()
        compose.setContent {
            merchant = rememberCoroutineScope()
            key(mount.intValue) {
                PaymentElement(controller, order("bound")) { events += it }
            }
        }
        waitFor(controller, "ready") { events.contains(EmbeddedEvent.Ready) }
        backend.fail(order("next"))
        val update = merchantUpdate(controller, order("next"))
        waitFor(controller, "stale") { update.isCompleted && controller.isStaleOrder }

        compose.runOnUiThread {
            mount.intValue++
            Snapshot.sendApplyNotifications()
        }
        settle()
        assertTrue(controller.isStaleOrder)
        assertTrue(!controller.isInteractionEnabled)
    }

    @Test
    fun intentDuringPaymentBindsOnceProcessingEnds() {
        val controller = controller()
        val intent = mutableStateOf(order("bound"))
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, intent.value) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        waitFor(controller, "ready == 1") { ready == 1 }

        setProcessing(controller, true)
        setIntent(intent, order("next"))
        settle()
        assertEquals(order("bound"), controller.boundIntent)

        setProcessing(controller, false)
        waitFor(controller, "controller.boundIntent == order('next')") { controller.boundIntent == order("next") }
        settle()
        assertEquals(1, ready)
    }

    @Test
    fun merchantUpdateStartedWhenProcessingEndsIsNotCancelled() {
        val controller = controller()
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("bound"))
        }
        waitFor(controller, "controller.sheetState != null") { controller.sheetState != null }

        setProcessing(controller, true)
        settle()
        backend.hold(order("next"))
        lateinit var update: Deferred<PaymentIntent>
        compose.runOnUiThread {
            controller.setProcessingForTest(false)
            Snapshot.sendApplyNotifications()
            update = merchant.async(start = CoroutineStart.UNDISPATCHED) { controller.updateOrder { order("next") } }
        }
        waitFor(controller, "load started") { backend.isLoading(order("next")) }
        settle()
        backend.release(order("next"))
        waitFor(controller, "update.isCompleted") { update.isCompleted }
        assertEquals(null, update.getCompletionExceptionOrNull())
        assertEquals(order("next"), controller.boundIntent)
    }

    @Test
    fun supersededFirstBindStillEmitsReadyWhenTheNewerBindSucceeds() {
        val controller = controller()
        backend.hold(order("first"))
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("first")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        waitFor(controller, "load started") { backend.isLoading(order("first")) }
        val newer = merchantUpdate(controller, order("second"))
        backend.release(order("first"))
        waitFor(controller, "ready == 1") { ready == 1 }
        assertEquals(order("second"), controller.boundIntent)
        assertTrue(newer.getCompleted().isSuccess)
    }

    @Test
    fun supersededFirstBindDoesNotEmitReadyWhenTheNewerBindFails() {
        var results = 0
        val controller = controller { results++ }
        backend.hold(order("first"))
        backend.fail(order("second"))
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            PaymentElement(controller, order("first")) {
                if (it is EmbeddedEvent.Ready) ready++
            }
        }
        waitFor(controller, "load started") { backend.isLoading(order("first")) }
        val newer = merchantUpdate(controller, order("second"))
        waitFor(controller, "newer.isCompleted") { newer.isCompleted }
        backend.release(order("first"))
        waitFor(controller, "failure text shown") {
            compose.onAllNodesWithText(failureText).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(controller.bindError != null)
        settle()
        assertEquals(0, ready)
        assertEquals(1, results)
        assertTrue(newer.getCompleted().exceptionOrNull() is com.xmoney.payments.model.PaymentError)
    }

    @Test
    fun remountEmitsReadyAgain() {
        val controller = controller()
        var ready = 0
        val generation = mutableIntStateOf(0)
        compose.setContent {
            merchant = rememberCoroutineScope()
            key(generation.intValue) {
                PaymentElement(controller, order("bound")) {
                    if (it is EmbeddedEvent.Ready) ready++
                }
            }
        }
        waitFor(controller, "ready == 1") { ready == 1 }
        compose.runOnUiThread {
            generation.intValue = 1
            Snapshot.sendApplyNotifications()
        }
        waitFor(controller, "ready == 2") { ready == 2 }
        assertEquals(2, ready)
    }

    private fun controller(onResult: (PaymentResult) -> Unit = {}): EmbeddedPaymentController {
        val controller = EmbeddedPaymentController(
            configuration = PaymentConfig(publicKey = "pk_test_x"),
            activity = activity,
            onResult = onResult,
        )
        controller.session = backend.session(activity)
        return controller
    }

    private fun merchantUpdate(controller: EmbeddedPaymentController, order: PaymentIntent): Deferred<Result<Unit>> {
        lateinit var job: Deferred<Result<Unit>>
        compose.runOnUiThread {
            job = merchant.async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { controller.updateOrder(order) }
            }
        }
        return job
    }

    private fun setProcessing(controller: EmbeddedPaymentController, processing: Boolean) {
        compose.runOnUiThread {
            controller.setProcessingForTest(processing)
            Snapshot.sendApplyNotifications()
        }
    }

    private fun setIntent(state: androidx.compose.runtime.MutableState<PaymentIntent>, value: PaymentIntent) {
        compose.runOnUiThread {
            state.value = value
            Snapshot.sendApplyNotifications()
        }
    }

    private fun waitFor(controller: EmbeddedPaymentController, what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(5_000, condition)
        } catch (e: Throwable) {
            throw AssertionError(
                "Timed out waiting for $what: bound=${controller.boundIntent?.orderPayload} " +
                    "state=${controller.sheetState != null} updating=${controller.isUpdatingOrder} " +
                    "processing=${controller.isProcessing} stale=${controller.isStaleOrder} " +
                    "error=${controller.bindError}",
                e,
            )
        }
    }

    /** Lets pending frames, effects, and draw timeouts run so absent events can be asserted. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }
}
