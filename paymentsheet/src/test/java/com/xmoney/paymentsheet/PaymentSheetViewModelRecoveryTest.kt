package com.xmoney.paymentsheet

import androidx.lifecycle.SavedStateHandle
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.engine.PendingPayment
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.paymentsheet.internal.PaymentSheetViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentSheetViewModelRecoveryTest {
    private val intent = PaymentIntent(OrderPayload("e30="), OrderChecksum("cs"))
    private val config = PaymentConfig(publicKey = "pk_test_android")

    private fun viewModel(handle: SavedStateHandle): PaymentSheetViewModel {
        val session = PaymentSession(config, intent, RuntimeEnvironment.getApplication())
        return PaymentSheetViewModel(handle, session, config.resolve(intent), "req-1")
    }

    @Test
    fun pendingPaymentSurvivesAProcessRestart() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        assertNull(first.pendingToRecover)

        val pending = PendingPayment("tx-1", "sess-1")
        first.session.onPendingChanged?.invoke(pending)
        val saved = handle.get<PendingPayment>(PaymentSheetViewModel.KEY_PENDING)
        assertEquals(pending, saved)

        val restored = viewModel(SavedStateHandle(mapOf(PaymentSheetViewModel.KEY_PENDING to saved)))
        assertEquals(pending, restored.pendingToRecover)
    }

    @Test
    fun aSettledPaymentIsNotRecovered() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.session.onPendingChanged?.invoke(PendingPayment("tx-1", "sess-1"))
        viewModel.session.onPendingChanged?.invoke(null)
        assertNull(viewModel.pendingToRecover)
        assertNull(handle.get<PendingPayment>(PaymentSheetViewModel.KEY_PENDING))
    }
}
