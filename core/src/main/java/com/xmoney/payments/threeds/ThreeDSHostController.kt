package com.xmoney.payments.threeds

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.xmoney.payments.engine.ThreeDSPresenter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class ThreeDSHostController(
    private val activity: FragmentActivity,
    private val localeProvider: () -> String,
) : ThreeDSPresenter, ThreeDSListener {
    private var activeDialog: ThreeDSDialog? = null
    private var activeReturnUrlMatcher: ((String) -> Boolean)? = null
    private var threeDSContinuation: ((Boolean) -> Unit)? = null

    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    override suspend fun presentThreeDS(url: String, returnUrlMatcher: (String) -> Boolean): Boolean =
        suspendCancellableCoroutine { cont ->
            activeReturnUrlMatcher = returnUrlMatcher
            threeDSContinuation = { success ->
                activeReturnUrlMatcher = null
                activeDialog = null
                if (cont.isActive) cont.resume(success)
            }
            cont.invokeOnCancellation {
                activeReturnUrlMatcher = null
                threeDSContinuation = null
                val dialog = activeDialog
                activeDialog = null
                runCatching { dialog?.dismissAllowingStateLoss() }
            }
            val dialog = ThreeDSDialog.newInstance(url, localeProvider()).also {
                it.hostListener = this
            }
            activeDialog = dialog
            dialog.show(fragmentManager, ThreeDSDialog.TAG)
        }

    override fun dismissThreeDS() {
        activeDialog?.dismissProgrammatically()
        activeDialog = null
    }

    override fun shouldInterceptThreeDSUrl(url: String): Boolean =
        activeReturnUrlMatcher?.invoke(url) == true

    override fun onThreeDSFinished(success: Boolean) {
        threeDSContinuation?.invoke(success)
        threeDSContinuation = null
    }
}
