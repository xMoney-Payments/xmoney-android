package com.xmoney.paymentelement

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    private fun controller(
        onResult: (PaymentResult) -> Unit = {},
    ): EmbeddedPaymentController =
        EmbeddedPaymentController(
            configuration = PaymentConfig(publicKey = "pk_test_x"),
            activity = activity,
            onResult = onResult,
        )

    private object ExpectedFailure : RuntimeException()
}
