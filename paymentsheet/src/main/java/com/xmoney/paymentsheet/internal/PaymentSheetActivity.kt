package com.xmoney.paymentsheet.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.Transaction
import kotlinx.parcelize.Parcelize

class PaymentSheetActivity : FragmentActivity(), PaymentSheetViewModelOwner {
    override lateinit var paymentSheetViewModel: PaymentSheetViewModel

    private var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val parcelable = intent.getParcelableExtra<PaymentSheetRequestParcelable>(EXTRA_REQUEST)
        val session = if (requestId.isNotBlank()) CheckoutSessionRegistry.get(requestId) else null
        val config = session?.config
            ?: parcelable?.config
            ?: run {
                finishWithResult(PaymentResult(PaymentResult.Status.CANCELED, null, null, null))
                return
            }
        if (requestId.isBlank()) {
            requestId = parcelable?.requestId.orEmpty()
        }
        if (requestId.isBlank()) {
            finishWithResult(PaymentResult(PaymentResult.Status.CANCELED, null, null, null))
            return
        }

        CheckoutSessionRegistry.bindHost(requestId, this)

        val factory = PaymentSheetViewModel.Factory(applicationContext, config, requestId)
        paymentSheetViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[PaymentSheetViewModel::class.java]
        paymentSheetViewModel.onEvent = { event ->
            PaymentSheetEventBridge.emit(requestId, event)
        }
        paymentSheetViewModel.onComplete = { result ->
            val registered = CheckoutSessionRegistry.get(requestId)
            registered?.onResult?.invoke(result)
            CheckoutSessionRegistry.remove(requestId)
            finishWithResult(result)
        }

        if (savedInstanceState == null) {
            PaymentSheetFragment().show(supportFragmentManager, PaymentSheetFragment.TAG)
        }
    }

    private fun finishWithResult(result: PaymentResult) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, ParcelableResult.from(result)))
        finish()
    }

    companion object {
        const val EXTRA_REQUEST = "xmoney_payment_sheet_request"
        const val EXTRA_REQUEST_ID = "xmoney_payment_sheet_request_id"
        const val EXTRA_RESULT = "xmoney_payment_sheet_result"

        fun createIntent(context: Context, request: PaymentSheetRequestParcelable): Intent {
            return Intent(context, PaymentSheetActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, request.requestId)
                putExtra(EXTRA_REQUEST, request)
            }
        }
    }
}

@Parcelize
data class PaymentSheetRequestParcelable(
    val config: ResolvedPaymentConfig,
    val requestId: String,
) : Parcelable {
    fun toRequest(): PaymentSheetRequest = PaymentSheetRequest(config, requestId)

    companion object {
        fun from(request: PaymentSheetRequest): PaymentSheetRequestParcelable =
            PaymentSheetRequestParcelable(request.config, request.requestId)
    }
}

@Parcelize
data class ParcelableResult(
    val status: String,
    val transaction: Transaction?,
    val errorCode: String?,
    val errorMessage: String?,
) : Parcelable {
    fun toResult(): PaymentResult = PaymentResult(
        status = PaymentResult.Status.entries.first { it.raw == status },
        transaction = transaction,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    companion object {
        fun from(result: PaymentResult): ParcelableResult = ParcelableResult(
            status = result.status.raw,
            transaction = result.transaction,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
        )
    }
}
