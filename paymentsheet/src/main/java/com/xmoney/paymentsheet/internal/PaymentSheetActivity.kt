package com.xmoney.paymentsheet.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.engine.OrderConsumption
import com.xmoney.payments.model.Transaction
import kotlinx.parcelize.Parcelize

class PaymentSheetActivity : FragmentActivity(), PaymentSheetViewModelOwner {
    override lateinit var paymentSheetViewModel: PaymentSheetViewModel

    private var requestId: String = ""
    private var didFinish = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val parcelable = intent.getParcelableExtra<PaymentSheetRequestParcelable>(EXTRA_REQUEST)
        val session = if (requestId.isNotBlank()) CheckoutSessionRegistry.get(requestId) else null
        val config = session?.config
            ?: parcelable?.config
            ?: run {
                deliverAndFinish(EngineResult(EngineResult.Status.CANCELED, null, null, null))
                return
            }
        if (requestId.isBlank()) {
            requestId = parcelable?.requestId.orEmpty()
        }
        if (requestId.isBlank()) {
            deliverAndFinish(EngineResult(EngineResult.Status.CANCELED, null, null, null))
            return
        }

        CheckoutSessionRegistry.bindHost(requestId, this)
        CheckoutSessionRegistry.bindCloseTarget(requestId, object : CheckoutCloseTarget {
            override fun requestClose(): Boolean {
                val fragment = supportFragmentManager.findFragmentByTag(PaymentSheetFragment.TAG)
                    as? PaymentSheetFragment
                return if (fragment != null) {
                    fragment.requestClose()
                } else {
                    deliverAndFinish(EngineResult(EngineResult.Status.CANCELED, null, null, null))
                    true
                }
            }
        })
        if (didFinish) return

        val factory = PaymentSheetViewModel.Factory(applicationContext, config, requestId)
        paymentSheetViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[PaymentSheetViewModel::class.java]
        paymentSheetViewModel.onEvent = { event ->
            PaymentSheetEventBridge.emit(requestId, event)
        }
        paymentSheetViewModel.onComplete = { result ->
            deliverAndFinish(result)
        }

        if (savedInstanceState == null) {
            PaymentSheetFragment().show(supportFragmentManager, PaymentSheetFragment.TAG)
        }
    }

    private fun deliverAndFinish(result: EngineResult) {
        if (didFinish) return
        didFinish = true
        val registered = CheckoutSessionRegistry.get(requestId)
        registered?.onResult?.invoke(OrderConsumption.merchantResult(result))
        if (requestId.isNotBlank()) {
            CheckoutSessionRegistry.remove(requestId)
        }
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
    fun toResult(): EngineResult = EngineResult(
        status = EngineResult.Status.entries.first { it.raw == status },
        transaction = transaction,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    companion object {
        fun from(result: EngineResult): ParcelableResult = ParcelableResult(
            status = result.status.raw,
            transaction = result.transaction,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
        )
    }
}
