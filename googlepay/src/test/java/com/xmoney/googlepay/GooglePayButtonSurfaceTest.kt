package com.xmoney.googlepay

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import com.xmoney.googlepay.FakeBackend.Companion.order
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.xmoney.payments.engine.DigitalWalletFactory
import org.junit.After
import org.junit.Assert.assertEquals
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
        compose.waitUntil(5_000) { controller.bindError != null }
        compose.onNodeWithText(failureText).assertExists()
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
