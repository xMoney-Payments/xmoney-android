package com.xmoney.googlepay.internal

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.PaymentData
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.engine.ThreeDSPresenter
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class GooglePayWalletController(
    resultCaller: ActivityResultCaller? = null,
) {
    private var activity: ComponentActivity? = null
    private var engine: PaymentEngine? = null
    private var threeDSPresenter: ThreeDSPresenter? = null
    private var handler: GooglePayHandler? = null

    private var onProcessing: ((Boolean) -> Unit)? = null
    private var onResult: ((PaymentResult) -> Unit)? = null
    private var scope: CoroutineScope? = null

    private var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>? =
        resultCaller?.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { handleResolutionResult(it) }

    fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        resolutionLauncher = launcher
    }

    fun handleResolutionResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val paymentData = result.data?.let { PaymentData.getFromIntent(it) }
                if (paymentData == null) {
                    onProcessing?.invoke(false)
                    return
                }
                submitToken(GooglePayHandler.extractToken(paymentData))
            }
            Activity.RESULT_CANCELED -> onProcessing?.invoke(false)
            else -> emitResult(
                PaymentResult(
                    PaymentResult.Status.FAILED,
                    null,
                    "GOOGLE_PAY",
                    "Google Pay was not completed",
                ),
            )
        }
    }

    fun attach(
        activity: ComponentActivity,
        engine: PaymentEngine,
        threeDSPresenter: ThreeDSPresenter,
        onProcessing: (Boolean) -> Unit,
        onResult: (PaymentResult) -> Unit,
        scope: CoroutineScope = activity.lifecycleScope,
    ) {
        this.activity = activity
        this.engine = engine
        this.threeDSPresenter = threeDSPresenter
        this.scope = scope
        this.onProcessing = onProcessing
        this.onResult = onResult
        handler = GooglePayHandler(activity, engine.env)
    }

    suspend fun prepare(): GooglePayAvailability {
        val state = prepareSheetState()
        return GooglePayAvailability(
            available = state.googlePayAvailable,
            ready = state.googlePayReady,
            allowedPaymentMethodsJson = state.googlePayAllowedPaymentMethods,
            orderInfo = state.orderInfo,
        )
    }

    suspend fun prepareSheetState(): com.xmoney.payments.engine.SheetState {
        val engine = engine ?: error("GooglePayController.attach() must be called first")
        val handler = handler ?: error("GooglePayController.attach() must be called first")
        val baseState = engine.load(googlePayConfigured = false)
        if (!engine.config.paymentMethods.googlePay.enabled) {
            return baseState
        }
        val params = runCatching { engine.walletParams("googlePay") }.getOrNull()
            ?: return baseState
        val methods = handler.allowedPaymentMethodsJson(params)
        val configured = methods.isNotBlank()
        val ready = configured && runCatching { handler.isReadyToPay(params) }.getOrDefault(false)
        return baseState.copy(
            googlePayAvailable = configured,
            googlePayAllowedPaymentMethods = methods.takeIf { configured },
            googlePayReady = ready,
        )
    }

    fun startPayment(orderInfo: OrderPayloadInfo) {
        val engine = engine ?: return
        val handler = handler ?: return
        val scope = scope ?: return
        onProcessing?.invoke(true)
        scope.launch {
            try {
                val params = engine.walletParams("googlePay")
                val request = handler.loadPaymentDataRequest(params, orderInfo)
                val task = handler.paymentsClient.loadPaymentData(request)
                task.addOnCompleteListener { completed ->
                    if (completed.isSuccessful) {
                        val paymentData = completed.result
                        if (paymentData != null) {
                            submitToken(GooglePayHandler.extractToken(paymentData))
                        } else {
                            onProcessing?.invoke(false)
                        }
                        return@addOnCompleteListener
                    }
                    val exception = completed.exception
                    if (exception is ResolvableApiException) {
                        val launcher = resolutionLauncher
                        if (launcher != null) {
                            launcher.launch(
                                IntentSenderRequest.Builder(exception.resolution).build(),
                            )
                        } else {
                            emitResult(
                                PaymentResult.failed(
                                    "GOOGLE_PAY",
                                    "Google Pay resolution launcher not registered",
                                ),
                            )
                        }
                    } else {
                        emitResult(
                            PaymentResult.failed(
                                "GOOGLE_PAY",
                                exception?.message ?: PaymentError.GENERIC_GOOGLE_PAY,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                emitResult(
                    PaymentResult.failed("GOOGLE_PAY", e.message ?: PaymentError.GENERIC_GOOGLE_PAY),
                )
            }
        }
    }

    private fun submitToken(token: String) {
        val engine = engine ?: return
        val presenter = threeDSPresenter ?: return
        val scope = scope ?: return
        scope.launch {
            try {
                val result = engine.submitWallet("googlePay", token, presenter)
                emitResult(result)
            } catch (e: Exception) {
                emitResult(
                    if (e is PaymentError) PaymentResult.failed(e)
                    else PaymentResult.failed("GOOGLE_PAY", e.message ?: PaymentError.GENERIC_GOOGLE_PAY),
                )
            }
        }
    }

    private fun emitResult(result: PaymentResult) {
        onResult?.invoke(result)
    }
}
