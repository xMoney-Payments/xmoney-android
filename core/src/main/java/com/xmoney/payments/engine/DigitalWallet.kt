package com.xmoney.payments.engine

import android.content.Context
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.WalletAppearance

/** Wallet authorizer implemented by Google Pay (Play Wallet lives outside core). */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface DigitalWalletAuthorizing {
    /** `true` once the user authorized a payment (token submit / 3DS may follow). */
    val didAuthorizePayment: Boolean

    /**
     * `true` when Google Pay already returned payment data (e.g. after activity
     * recreation) and [start] should submit it instead of opening the wallet UI.
     */
    val hasPendingWalletAuthorization: Boolean
        get() = false

    fun bindResolutionLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>)

    fun handleResolutionResult(result: ActivityResult)

    suspend fun start(): EngineResult
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class WalletButtonArgs(
    val allowedPaymentMethods: String,
    val appearance: WalletAppearance,
    val enabled: Boolean,
    val isDarkBackground: Boolean,
    val onClick: () -> Unit,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun interface WalletButtonFactory {
    fun create(context: Context, args: WalletButtonArgs): View
}

/**
 * Runtime hooks so Core can stay free of Play Wallet / googlepay.
 * The Google Pay module overwrites these via `GooglePay.register()`.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object DigitalWalletFactory {
    var makeGooglePay: (
        (PaymentEngine, ThreeDSPresenter, FragmentActivity) -> DigitalWalletAuthorizing
    )? = null

    var decorateSheetState: (
        suspend (PaymentEngine, SheetState, FragmentActivity) -> SheetState
    )? = null

    var buttonFactory: WalletButtonFactory? = null
}
