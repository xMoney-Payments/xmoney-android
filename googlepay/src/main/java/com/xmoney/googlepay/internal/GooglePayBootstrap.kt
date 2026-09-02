package com.xmoney.googlepay.internal

import android.util.TypedValue
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
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
            val heightPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                56f,
                context.resources.displayMetrics,
            ).toInt()
            val argsState = mutableStateOf(args)
            ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    heightPx,
                )
                clipChildren = true
                clipToPadding = true
                tag = argsState
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val current = argsState.value
                    GooglePayButton(
                        appearance = current.appearance,
                        allowedPaymentMethods = current.allowedPaymentMethods,
                        enabled = current.enabled,
                        onClick = current.onClick,
                        isDarkBackground = current.isDarkBackground,
                    )
                }
            }
        }
    }
}
