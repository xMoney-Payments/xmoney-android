package com.xmoney.paymentsheet.internal

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.xmoney.googlepay.internal.GooglePayWalletController
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.paymentelement.ui.UIHelpers
import com.xmoney.paymentelement.ui.XCoinFlipLoader
import com.xmoney.payments.engine.PaymentEngine
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentResult
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.threeds.ThreeDSHostController
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentsheet.ui.PaymentSheetContent
import kotlinx.coroutines.launch

class PaymentSheetFragment : BottomSheetDialogFragment() {
    private sealed class UiState {
        data object Loading : UiState()
        data class Loaded(val state: SheetState) : UiState()
        data class Failed(val message: String) : UiState()
    }

    private val viewModel: PaymentSheetViewModel
        get() = (requireActivity() as PaymentSheetViewModelOwner).paymentSheetViewModel
    private val engine get() = viewModel.engine

    private var uiState by mutableStateOf<UiState>(UiState.Loading)
    private var isProcessing by mutableStateOf(false)
    private var didFinish = false

    private val threeDSHostController by lazy {
        ThreeDSHostController(requireActivity()) { viewModel.config.options.locale }
    }

    private val googlePayController = GooglePayWalletController(this)

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
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isDraggable = false
            }
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
        googlePayController.attach(
            activity = requireActivity(),
            engine = viewModel.engine,
            threeDSPresenter = threeDSHostController,
            scope = lifecycleScope,
            onProcessing = { processing -> setProcessingState(processing) },
            onResult = { result ->
                finish(result)
            },
        )
        loadState(viewModel)
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
                    ) { XCoinFlipLoader(color = theme.primary) }
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
                onCancel = { finish(PaymentResult(PaymentResult.Status.CANCELED, null, null, null)) },
            )
        }
    }

    private fun loadState(session: PaymentSheetViewModel) {
        lifecycleScope.launch {
            try {
                val state = loadSheetState(session.engine)
                uiState = UiState.Loaded(state)
                session.onEvent(PaymentSheetEvent.Ready)
            } catch (e: PaymentError) {
                finish(PaymentResult.failed(e))
            } catch (e: Exception) {
                finish(PaymentResult.failed("LOAD_ERROR", e.message ?: PaymentError.GENERIC_LOAD))
            }
        }
    }

    private suspend fun loadSheetState(engine: PaymentEngine): SheetState {
        return if (engine.config.paymentMethods.googlePay.enabled) {
            googlePayController.prepareSheetState()
        } else {
            engine.load(googlePayConfigured = false)
        }
    }

    private fun startGooglePay() {
        val state = (uiState as? UiState.Loaded)?.state ?: return
        googlePayController.startPayment(state.orderInfo)
    }

    private fun payWithCard(input: CardInput) {
        val invalid = CardFieldValidators.validateCardNumber(input.number) != null ||
            CardFieldValidators.validateExpiry(input.expiryMonth, input.expiryYear) != null ||
            CardFieldValidators.validateCVV(input.cvv) != null ||
            CardFieldValidators.validateHolderName(input.holderName) != null
        if (invalid) return
        engine.onCardHolderVerification =
            CheckoutSessionRegistry.get(viewModel.requestId)?.onCardHolderVerification
                ?: viewModel.config.card.cardHolderVerification?.onCardHolderVerification
        runSubmission { engine.submitNewCard(input, threeDSHostController) }
    }

    private fun paySavedCard(card: SavedCard) =
        runSubmission { engine.submitSavedCard(card.id, threeDSHostController) }

    private fun deleteSavedCard(card: SavedCard) {
        lifecycleScope.launch {
            runCatching { engine.deleteSavedCard(card.id) }
            runCatching {
                val refreshed = engine.refreshSavedCards()
                val current = (uiState as? UiState.Loaded)?.state ?: return@runCatching
                uiState = UiState.Loaded(current.copy(savedCards = refreshed))
            }
        }
    }

    private fun runSubmission(block: suspend () -> PaymentResult) {
        setProcessingState(true)
        lifecycleScope.launch {
            try {
                val result = block()
                finish(result)
            } catch (e: PaymentError) {
                finish(PaymentResult.failed(e))
            } catch (e: Exception) {
                finish(PaymentResult.failed("PAYMENT_ERROR", e.message ?: PaymentError.GENERIC_PAYMENT))
            }
        }
    }

    private fun setProcessingState(processing: Boolean) {
        isProcessing = processing
        viewModel.onEvent(PaymentSheetEvent.Processing(processing))
    }

    private fun finish(result: PaymentResult) {
        if (didFinish) return
        didFinish = true
        viewModel.finish(result)
        dismissAllowingStateLoss()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!didFinish) {
            didFinish = true
            viewModel.finish(PaymentResult(PaymentResult.Status.CANCELED, null, null, null))
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
