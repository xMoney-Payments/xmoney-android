package com.xmoney.paymentsheet

import com.xmoney.paymentsheet.internal.PaymentSheetActivity
import com.xmoney.paymentsheet.internal.PaymentSheetRequest
import com.xmoney.paymentsheet.internal.PaymentSheetRequestParcelable

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.internal.CheckoutSessionRegistry
import com.xmoney.googlepay.GooglePay
import java.util.UUID

/**
 * Public entry point for presenting the native xMoney checkout payment sheet.
 *
 * Instance-based (not a singleton) so merchants can hold multiple configurations
 * and the object remains testable.
 */
class PaymentSheet(
    private val configuration: PaymentConfig,
) {
    private var lastRequestId: String? = null

    /**
     * Present the payment sheet for [intent]. [onEvent] receives interim UI
     * events; [onResult] receives the terminal outcome.
     *
     * A second [present] while this instance's sheet is idle dismisses the
     * previous host ([PaymentResult.Canceled] on that present's callbacks)
     * then starts a new one. While a charge is in flight the call is a no-op.
     */
    fun present(
        activity: FragmentActivity,
        intent: PaymentIntent,
        onEvent: (PaymentSheetEvent) -> Unit = {},
        onResult: (PaymentResult) -> Unit,
    ) {
        GooglePay.register()
        if (!CheckoutSessionRegistry.replaceIdleHost(lastRequestId)) return
        val paymentConfig = configuration.resolve(intent)
        val requestId = UUID.randomUUID().toString()
        lastRequestId = requestId

        CheckoutSessionRegistry.register(
            requestId,
            CheckoutSessionRegistry.Session(
                onEvent = onEvent,
                onResult = onResult,
                onCardHolderVerification =
                    configuration.card.cardHolderVerification?.onCardHolderVerification,
                config = paymentConfig,
            ),
        )

        val request = PaymentSheetRequest(paymentConfig, requestId = requestId)
        val intent = PaymentSheetActivity.createIntent(
            activity,
            PaymentSheetRequestParcelable.from(request),
        )
        activity.startActivity(intent)
    }

    /** Dismiss an in-flight payment sheet started by this instance, if still open. */
    fun dismiss() {
        CheckoutSessionRegistry.finishHost(lastRequestId)
    }
}
