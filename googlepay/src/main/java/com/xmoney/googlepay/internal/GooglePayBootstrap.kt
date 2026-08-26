package com.xmoney.googlepay.internal

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.xmoney.googlepay.ui.GooglePayButton
import com.xmoney.payments.engine.DigitalWalletFactory
import com.xmoney.payments.engine.WalletButtonFactory

internal object GooglePayBootstrap {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        DigitalWalletFactory.makeGooglePay = { engine, presenter, activity ->
            GooglePayWalletController().also { controller ->
                controller.attach(activity, engine, presenter)
            }
        }
        DigitalWalletFactory.decorateSheetState = { engine, state, activity ->
            GooglePayWalletController.decorate(engine, activity, state)
        }
        DigitalWalletFactory.buttonFactory = WalletButtonFactory { context, args ->
            ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    GooglePayButton(
                        appearance = args.appearance,
                        allowedPaymentMethods = args.allowedPaymentMethods,
                        enabled = args.enabled,
                        onClick = args.onClick,
                        isDarkBackground = args.isDarkBackground,
                    )
                }
            }
        }
    }
}
