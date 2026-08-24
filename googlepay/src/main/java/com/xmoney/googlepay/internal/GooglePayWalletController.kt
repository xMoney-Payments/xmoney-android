package com.xmoney.googlepay.internal

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.PaymentData
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.engine.ThreeDSPresenter
import com.xmoney.payments.model.OrderPayloadDecoder
import com.xmoney.payments.model.OrderPayloadInfo
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.engine.EngineResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GooglePayWalletController(
    resultCaller: ActivityResultCaller? = null,
) : DigitalWalletAuthorizing {
    private var activity: ComponentActivity? = null
    private var engine: PaymentEngine? = null
    private var threeDSPresenter: ThreeDSPresenter? = null
    private var handler: GooglePayHandler? = null
    private var scope: CoroutineScope? = null

    private var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>? =
        resultCaller?.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { handleResolutionResult(it) }
    private var autoRegisteredLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingPaymentData: PaymentData? = null

    private var startContinuation: CancellableContinuation<EngineResult>? = null

    override var didAuthorizePayment: Boolean = false
        private set

    override val hasPendingWalletAuthorization: Boolean
        get() = pendingPaymentData != null

    override fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (resolutionLauncher === launcher) return
        unregisterAutoLauncher()
        resolutionLauncher = launcher
    }

    fun attach(
        activity: ComponentActivity,
        engine: PaymentEngine,
        threeDSPresenter: ThreeDSPresenter,
        scope: CoroutineScope = activity.lifecycleScope,
    ) {
        this.activity = activity
        this.engine = engine
        this.threeDSPresenter = threeDSPresenter
        this.scope = scope
        handler = GooglePayHandler(activity, engine.env)
    }

    private fun ensureResolutionLauncher(): ActivityResultLauncher<IntentSenderRequest>? {
        resolutionLauncher?.let { return it }
        val host = activity ?: return null
        val registered = host.activityResultRegistry.register(
            "xmoney-google-pay-resolution-${System.identityHashCode(this)}",
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { handleResolutionResult(it) }
        autoRegisteredLauncher = registered
        resolutionLauncher = registered
        return registered
    }

    private fun unregisterAutoLauncher() {
        autoRegisteredLauncher?.unregister()
        if (resolutionLauncher === autoRegisteredLauncher) {
            resolutionLauncher = null
        }
        autoRegisteredLauncher = null
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

    suspend fun prepareSheetState(): SheetState {
        val engine = engine ?: error("GooglePayController.attach() must be called first")
        val handler = handler ?: error("GooglePayController.attach() must be called first")
        val baseState = engine.load(googlePayConfigured = true)
        return decorate(engine, handler, baseState)
    }

    override suspend fun start(): EngineResult {
        val engine = engine ?: return failure("Google Pay is not available")
        return start(OrderPayloadDecoder.info(engine.config.orderPayload))
    }

    suspend fun start(orderInfo: OrderPayloadInfo): EngineResult {
        val engine = engine ?: return failure("Google Pay is not available")
        val handler = handler ?: return failure("Google Pay is not available")
        val pending = pendingPaymentData
        if (pending != null) {
            pendingPaymentData = null
            didAuthorizePayment = true
            return suspendCancellableCoroutine { cont ->
                startContinuation = cont
                submitToken(pending)
            }
        }
        didAuthorizePayment = false
        return suspendCancellableCoroutine { cont ->
            startContinuation = cont
            val payScope = scope
            if (payScope == null) {
                cont.resume(failure("Google Pay is not available"))
                return@suspendCancellableCoroutine
            }
            val job = payScope.launch {
                try {
                    val params = engine.walletParams("googlePay")
                    val request = handler.loadPaymentDataRequest(params, orderInfo)
                    val task = handler.paymentsClient.loadPaymentData(request)
                    task.addOnCompleteListener { completed ->
                        if (!cont.isActive) return@addOnCompleteListener
                        if (completed.isSuccessful) {
                            val paymentData = completed.result
                            if (paymentData == null) {
                                resumeCanceled()
                                return@addOnCompleteListener
                            }
                            submitToken(paymentData)
                            return@addOnCompleteListener
                        }
                        val exception = completed.exception
                        if (exception is ResolvableApiException) {
                            val launcher = ensureResolutionLauncher()
                            if (launcher != null) {
                                launcher.launch(
                                    IntentSenderRequest.Builder(exception.resolution).build(),
                                )
                            } else {
                                resumeResult(
                                    EngineResult.failed(
                                        "GOOGLE_PAY",
                                        "Google Pay resolution launcher not registered",
                                    ),
                                )
                            }
                        } else {
                            resumeResult(
                                EngineResult.failed(
                                    "GOOGLE_PAY",
                                    exception?.message ?: PaymentError.GENERIC_GOOGLE_PAY,
                                ),
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PaymentError) {
                    resumeResult(EngineResult.failed(e))
                } catch (e: Exception) {
                    resumeResult(
                        EngineResult.failed(
                            "GOOGLE_PAY",
                            e.message ?: PaymentError.GENERIC_GOOGLE_PAY,
                        ),
                    )
                }
            }
            cont.invokeOnCancellation { job.cancel() }
        }
    }

    override fun handleResolutionResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val paymentData = result.data?.let { PaymentData.getFromIntent(it) }
                if (paymentData == null) {
                    resumeCanceled()
                    return
                }
                if (startContinuation != null) {
                    submitToken(paymentData)
                } else {
                    pendingPaymentData = paymentData
                }
            }
            Activity.RESULT_CANCELED -> resumeCanceled()
            else -> resumeResult(
                EngineResult(
                    EngineResult.Status.FAILED,
                    null,
                    "GOOGLE_PAY",
                    "Google Pay was not completed",
                ),
            )
        }
    }

    private fun submitToken(paymentData: PaymentData) {
        val engine = engine ?: run {
            resumeResult(failure("Google Pay is not available"))
            return
        }
        val presenter = threeDSPresenter ?: run {
            resumeResult(failure("Google Pay is not available"))
            return
        }
        val scope = scope ?: run {
            resumeResult(failure("Google Pay is not available"))
            return
        }
        didAuthorizePayment = true
        scope.launch {
            try {
                val token = GooglePayHandler.extractToken(paymentData)
                val result = engine.submitWallet("googlePay", token, presenter)
                resumeResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PaymentError) {
                resumeResult(EngineResult.failed(e))
            } catch (e: Exception) {
                resumeResult(
                    EngineResult.failed("GOOGLE_PAY", e.message ?: PaymentError.GENERIC_GOOGLE_PAY),
                )
            }
        }
    }

    private fun resumeCanceled() {
        resumeResult(
            EngineResult(EngineResult.Status.CANCELED, null, null, null),
        )
    }

    private fun resumeResult(result: EngineResult) {
        val cont = startContinuation
        startContinuation = null
        if (cont != null && cont.isActive) {
            cont.resume(result)
        }
    }

    private fun failure(message: String): EngineResult =
        EngineResult.failed(PaymentError.GooglePay(message))

    companion object {
        suspend fun decorate(
            engine: PaymentEngine,
            activity: FragmentActivity,
            state: SheetState,
        ): SheetState {
            if (!engine.config.paymentMethods.googlePay.enabled) return state
            val handler = GooglePayHandler(activity, engine.env)
            return decorate(engine, handler, state)
        }

        internal suspend fun decorate(
            engine: PaymentEngine,
            handler: GooglePayHandler,
            state: SheetState,
        ): SheetState {
            val params = runCatching { engine.walletParams("googlePay") }.getOrNull()
                ?: return state
            val methods = handler.allowedPaymentMethodsJson(params)
            val configured = methods.isNotBlank()
            val ready = configured && runCatching { handler.isReadyToPay(params) }.getOrDefault(false)
            return state.copy(
                googlePayAvailable = configured,
                googlePayAllowedPaymentMethods = methods.takeIf { configured },
                googlePayReady = ready,
            )
        }
    }
}
