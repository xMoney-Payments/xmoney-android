package com.xmoney.paymentelement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.model.PaymentResult

@Composable
fun rememberEmbeddedPayment(
    configuration: PaymentConfig,
    onResult: (PaymentResult) -> Unit,
): EmbeddedPaymentController {
    val activity = LocalContext.current as FragmentActivity
    val currentOnResult = rememberUpdatedState(onResult)
    val controller = remember(configuration, activity) {
        EmbeddedPaymentController(
            configuration = configuration,
            activity = activity,
            onResult = { currentOnResult.value(it) },
        )
    }
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        controller.handleWalletResolution(result)
    }
    SideEffect {
        controller.bindWalletResolutionLauncher(resolutionLauncher)
    }
    return controller
}
