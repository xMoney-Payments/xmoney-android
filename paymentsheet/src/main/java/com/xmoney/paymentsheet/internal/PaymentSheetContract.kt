package com.xmoney.paymentsheet.internal

import com.xmoney.paymentsheet.internal.ParcelableResult
import com.xmoney.paymentsheet.internal.PaymentSheetActivity
import com.xmoney.paymentsheet.internal.PaymentSheetRequest
import com.xmoney.paymentsheet.internal.PaymentSheetRequestParcelable

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.model.PaymentResult

class PaymentSheetContract : ActivityResultContract<PaymentSheetRequest, PaymentResult>() {
    override fun createIntent(context: Context, input: PaymentSheetRequest): Intent {
        return PaymentSheetActivity.createIntent(
            context,
            PaymentSheetRequestParcelable.from(input),
        )
    }

    @Suppress("DEPRECATION")
    override fun parseResult(resultCode: Int, intent: Intent?): PaymentResult {
        val parcelable = intent?.getParcelableExtra<ParcelableResult>(PaymentSheetActivity.EXTRA_RESULT)
        val engine = parcelable?.toResult() ?: EngineResult.canceled()
        return OrderConsumption.merchantResult(engine)
    }
}

data class PaymentSheetRequest(
    val config: ResolvedPaymentConfig,
    val requestId: String,
)
