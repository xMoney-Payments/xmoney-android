package com.xmoney.googlepay

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import com.xmoney.googlepay.FakeBackend.Companion.order
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.xmoney.payments.engine.DigitalWalletFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GooglePayButtonSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    private val activity: FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    private val backend = FakeBackend(inline = true)

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
    private lateinit var merchant: CoroutineScope
    private val failureText = Strings.text("sheet.loadFailed", "en")

    @Test
    fun unavailableWalletIsASettledStateAndEmitsReadyOnce() {
        val controller = controller()
        var ready = 0
        compose.setContent {
            GooglePayButton(controller, order("bound")) {
                if (it is GooglePayEvent.Ready) ready++
            }
        }
        compose.waitUntil(5_000) { ready == 1 }
        settle()
        assertEquals(1, ready)
    }

    @Test
    fun initialFailureShowsTheFailureStateWithoutReadyAndARetryEmitsIt() {
        var results = 0
        val controller = controller { results++ }
        backend.failAll = true
        var ready = 0
        compose.setContent {
            merchant = rememberCoroutineScope()
            GooglePayButton(controller, order("missing")) {
                if (it is GooglePayEvent.Ready) ready++
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(failureText).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(controller.bindError != null)
        settle()
        assertEquals(0, ready)
        assertEquals(1, results)

        backend.failAll = false
        compose.runOnUiThread {
            merchant.launch { runCatching { controller.updateOrder(order("missing")) } }
        }
        compose.waitUntil(5_000) { ready == 1 }
        compose.onNodeWithText(failureText).assertDoesNotExist()
    }

    @Test
    fun switchingBackToTheBoundIntentClearsTheStaleLock() {
        val controller = controller()
        val intent = mutableStateOf(order("bound"))
        compose.setContent {
            GooglePayButton(controller, intent.value)
        }
        compose.waitUntil(5_000) { controller.boundIntent == order("bound") }
        backend.fail(order("next"))
        setIntent(intent, order("next"))
        compose.waitUntil(5_000) { controller.isStaleOrder }

        setIntent(intent, order("bound"))
        compose.waitUntil(5_000) { !controller.isStaleOrder }
        assertTrue(controller.isInteractionEnabled)
    }

    @Test
    fun remountWithTheBoundIntentKeepsTheStaleLock() {
        val controller = controller()
        val mount = mutableIntStateOf(0)
        compose.setContent {
            merchant = rememberCoroutineScope()
            key(mount.intValue) {
                GooglePayButton(controller, order("bound"))
            }
        }
        compose.waitUntil(5_000) { controller.boundIntent == order("bound") }
        backend.fail(order("next"))
        compose.runOnUiThread {
            merchant.launch { runCatching { controller.updateOrder(order("next")) } }
        }
        compose.waitUntil(5_000) { controller.isStaleOrder }

        compose.runOnUiThread {
            mount.intValue++
            Snapshot.sendApplyNotifications()
        }
        settle()
        assertTrue(controller.isStaleOrder)
        assertFalse(controller.isInteractionEnabled)
    }

    private fun setIntent(state: MutableState<PaymentIntent>, value: PaymentIntent) {
        compose.runOnUiThread {
            state.value = value
            Snapshot.sendApplyNotifications()
        }
    }

    private fun controller(onResult: (PaymentResult) -> Unit = {}): GooglePayController {
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

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }
}
