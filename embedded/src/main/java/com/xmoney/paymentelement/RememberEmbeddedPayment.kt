package com.xmoney.paymentelement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentelement.EmbeddedPaymentController
import com.xmoney.googlepay.internal.GooglePayWalletController

@Composable
fun rememberEmbeddedPayment(
    configuration: PaymentConfig,
    onResult: (PaymentResult) -> Unit,
): EmbeddedPaymentController {
    val activity = LocalContext.current as FragmentActivity
    val googlePayController = remember { GooglePayWalletController() }
    val currentOnResult = rememberUpdatedState(onResult)
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        googlePayController.handleResolutionResult(result)
    }
    LaunchedEffect(resolutionLauncher) {
        googlePayController.bindResolutionLauncher(resolutionLauncher)
    }
    return remember(configuration, activity) {
        EmbeddedPaymentController(
            configuration = configuration,
            activity = activity,
            googlePayController = googlePayController,
            onResult = { currentOnResult.value(it) },
        )
    }
}
