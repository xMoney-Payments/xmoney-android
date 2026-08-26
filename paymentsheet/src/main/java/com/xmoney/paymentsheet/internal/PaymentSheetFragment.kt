package com.xmoney.paymentsheet.internal

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xmoney.googlepay.GooglePay
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.paymentelement.ui.UIHelpers
import com.xmoney.paymentelement.ui.XCoinFlipLoader
import com.xmoney.payments.engine.DigitalWalletAuthorizing
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.engine.EngineResult
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentsheet.ui.PaymentSheetContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PaymentSheetFragment : BottomSheetDialogFragment(), CheckoutCloseTarget {
    private sealed class UiState {
        data object Loading : UiState()
        data class Loaded(val state: SheetState) : UiState()
        data class Failed(val message: String) : UiState()
    }

    private val viewModel: PaymentSheetViewModel
        get() = (requireActivity() as PaymentSheetViewModelOwner).paymentSheetViewModel
    private val session get() = viewModel.session

    private var uiState by mutableStateOf<UiState>(UiState.Loading)
    private var isProcessing by mutableStateOf(false)
    private var didFinish = false
    private var authorizer: DigitalWalletAuthorizing? = null
    private var pendingWalletResult: androidx.activity.result.ActivityResult? = null
    private var sheetBehavior: BottomSheetBehavior<View>? = null
    private var behaviorCallback: BottomSheetBehavior.BottomSheetCallback? = null

    private val threeDSHostController by lazy {
        ThreeDSHostController(requireActivity()) { viewModel.config.options.locale }
    }

    private lateinit var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GooglePay.register()
        resolutionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            val wallet = authorizer
            if (wallet != null) {
                wallet.handleResolutionResult(result)
                if (wallet.hasPendingWalletAuthorization) {
                    startGooglePay()
                }
            } else {
                pendingWalletResult = result
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            sheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            bindSheetBehavior(sheet)
        }
    }

    private fun bindSheetBehavior(sheet: View) {
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isHideable = true
        behavior.isDraggable = !isProcessing
        if (sheetBehavior !== behavior) {
            behaviorCallback?.let { sheetBehavior?.removeBottomSheetCallback(it) }
            val callback = object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        requestClose()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            }
            behaviorCallback = callback
            behavior.addBottomSheetCallback(callback)
            sheetBehavior = behavior
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isNestedScrollingEnabled = true
            setContent { PaymentSheetRoot() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CheckoutSessionRegistry.bindCloseTarget(viewModel.requestId, this)
        loadState()
    }

    @androidx.compose.runtime.Composable
    private fun PaymentSheetRoot() {
        val vm = viewModel
        when (val current = uiState) {
            is UiState.Loading -> {
                val isDark = UIHelpers.isDarkMode(vm.config, isSystemInDarkTheme())
                val theme = vm.theme(isDark)
                SheetSurface(theme) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) { XCoinFlipLoader(color = CheckoutTheme.BrandPrimary) }
                }
            }

            is UiState.Failed -> {
                val isDark = UIHelpers.isDarkMode(vm.config, isSystemInDarkTheme())
                val theme = vm.theme(isDark)
                SheetSurface(theme) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(current.message, color = theme.error) }
                }
            }

            is UiState.Loaded -> PaymentSheetContent(
                config = vm.config,
                state = current.state,
                isProcessing = isProcessing,
                onPayCard = { payWithCard(it) },
                onSelectSaved = { paySavedCard(it) },
                onDeleteSaved = { deleteSavedCard(it) },
                onGooglePay = { startGooglePay() },
                onCancel = { requestClose() },
            )
        }
    }

    private fun loadState() {
        lifecycleScope.launch {
            try {
                val state = session.bind(viewModel.intent, requireActivity())
                authorizer = session.makeWalletAuthorizer(threeDSHostController, requireActivity())
                authorizer?.bindResolutionLauncher(resolutionLauncher)
                uiState = UiState.Loaded(state)
                viewModel.onEvent(PaymentSheetEvent.Ready)
                val pending = pendingWalletResult
                pendingWalletResult = null
                if (pending != null) {
                    authorizer?.handleResolutionResult(pending)
                    if (pending.resultCode == android.app.Activity.RESULT_OK &&
                        authorizer?.hasPendingWalletAuthorization == true
                    ) {
                        startGooglePay()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PaymentError) {
                finish(EngineResult.failed(e))
            } catch (e: Exception) {
                finish(EngineResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD))
            }
        }
    }

    private fun startGooglePay() {
        val wallet = authorizer ?: return
        if (!session.isInteractionEnabled) return
        setProcessingState(true)
        lifecycleScope.launch {
            val result = session.startWallet(wallet)
            if (result.status == EngineResult.Status.CANCELED && !session.isOrderConsumed) {
                setProcessingState(false)
                return@launch
            }
            finish(result)
        }
    }

    private fun payWithCard(input: CardInput) {
        val invalid = CardFieldValidators.validateCardNumber(input.number) != null ||
            CardFieldValidators.validateExpiry(input.expiryMonth, input.expiryYear) != null ||
            CardFieldValidators.validateCVV(input.cvv) != null ||
            CardFieldValidators.validateHolderName(input.holderName) != null
        if (invalid) return
        session.onCardHolderVerification =
            CheckoutSessionRegistry.get(viewModel.requestId)?.onCardHolderVerification
                ?: viewModel.config.card.cardHolderVerification?.onCardHolderVerification
        runSubmission { session.submitNewCard(input, threeDSHostController) }
    }

    private fun paySavedCard(card: SavedCard) =
        runSubmission { session.submitSavedCard(card.id, threeDSHostController) }

    private suspend fun deleteSavedCard(card: SavedCard) {
        val updated = session.deleteSavedCard(card.id)
        uiState = UiState.Loaded(updated)
    }

    private fun runSubmission(block: suspend () -> EngineResult) {
        setProcessingState(true)
        lifecycleScope.launch {
            try {
                val result = block()
                finish(result)
            } catch (e: CancellationException) {
                setProcessingState(false)
                throw e
            } catch (e: PaymentError) {
                finish(EngineResult.failed(e))
            } catch (e: Exception) {
                finish(EngineResult.failed("PAYMENT_ERROR", e.message ?: PaymentError.GENERIC_PAYMENT))
            }
        }
    }

    private fun setProcessingState(processing: Boolean) {
        isProcessing = processing
        isCancelable = !processing
        sheetBehavior?.isDraggable = !processing
        viewModel.onEvent(PaymentSheetEvent.Processing(processing))
    }

    override fun requestClose() {
        if (isProcessing) return
        finish(EngineResult(EngineResult.Status.CANCELED, null, null, null))
    }

    private fun finish(result: EngineResult) {
        if (didFinish) return
        didFinish = true
        viewModel.finish(result)
        dismissAllowingStateLoss()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!didFinish) {
            didFinish = true
            viewModel.finish(EngineResult(EngineResult.Status.CANCELED, null, null, null))
        }
    }

    companion object {
        const val TAG = "PaymentSheet"
    }
}

@androidx.compose.runtime.Composable
private fun SheetSurface(theme: CheckoutTheme, content: @androidx.compose.runtime.Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(theme.background),
    ) {
        content()
    }
}
