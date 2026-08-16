package com.xmoney.payments.threeds

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    private var onShownCallback: (() -> Unit)? = null
    private var lifecycleObserver: LifecycleEventObserver? = null
    private var showCommitted = false
    private var retryCount = 0
    private var retryRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    override suspend fun presentThreeDS(
        url: String,
        returnUrlMatcher: (String) -> Boolean,
        formMethod: String,
        params: Map<String, String>,
        onShown: () -> Unit,
    ): Boolean =
        suspendCancellableCoroutine { cont ->
            showCommitted = false
            retryCount = 0
            activeReturnUrlMatcher = returnUrlMatcher
            onShownCallback = onShown
            threeDSContinuation = { success ->
                if (cont.isActive) cont.resume(success)
            }
            val dialog = ThreeDSDialog.newInstance(url, localeProvider(), formMethod, params).also {
                it.hostListener = this
            }
            activeDialog = dialog
            cont.invokeOnCancellation {
                teardown()
                runCatching { dialog.dismissAllowingStateLoss() }
            }
            if (activity.isFinishing || activity.isDestroyed) {
                complete(false)
                return@suspendCancellableCoroutine
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) attemptShow(dialog)
                if (event == Lifecycle.Event.ON_DESTROY) complete(false)
            }
            lifecycleObserver = observer
            activity.lifecycle.addObserver(observer)
            attemptShow(dialog)
        }

    override fun dismissThreeDS() {
        activeDialog?.dismissProgrammatically()
        activeDialog = null
    }

    override fun shouldInterceptThreeDSUrl(url: String): Boolean =
        activeReturnUrlMatcher?.invoke(url) == true

    override fun onThreeDSShown() {
        val callback = onShownCallback
        onShownCallback = null
        callback?.invoke()
    }

    override fun onThreeDSFinished(success: Boolean) {
        complete(success)
    }

    private fun attemptShow(dialog: ThreeDSDialog) {
        if (showCommitted || dialog.isAdded) {
            markShown()
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            complete(false)
            return
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            scheduleRetry(dialog)
            return
        }
        if (commitShow(dialog)) {
            markShown()
            return
        }
        scheduleRetry(dialog)
    }

    private fun commitShow(dialog: ThreeDSDialog): Boolean {
        if (dialog.isAdded) return true
        return runCatching {
            if (dialog.isAdded) return@runCatching true
            fragmentManager.beginTransaction()
                .add(android.R.id.content, dialog, ThreeDSDialog.TAG)
                .commitNowAllowingStateLoss()
            dialog.isAdded
        }.getOrDefault(false)
    }

    private fun markShown() {
        showCommitted = true
        cancelRetries()
        removeLifecycleObserver()
    }

    private fun scheduleRetry(dialog: ThreeDSDialog) {
        if (showCommitted || retryCount >= MAX_SHOW_RETRIES) return
        retryCount++
        val runnable = Runnable { attemptShow(dialog) }
        retryRunnable = runnable
        mainHandler.postDelayed(runnable, SHOW_RETRY_DELAY_MS)
    }

    private fun cancelRetries() {
        val runnable = retryRunnable ?: return
        retryRunnable = null
        mainHandler.removeCallbacks(runnable)
    }

    private fun complete(success: Boolean) {
        val continuation = threeDSContinuation ?: return
        threeDSContinuation = null
        teardown()
        continuation(success)
    }

    private fun teardown() {
        cancelRetries()
        removeLifecycleObserver()
        activeReturnUrlMatcher = null
        activeDialog = null
        onShownCallback = null
    }

    private fun removeLifecycleObserver() {
        val observer = lifecycleObserver ?: return
        lifecycleObserver = null
        activity.lifecycle.removeObserver(observer)
    }

    companion object {
        private const val MAX_SHOW_RETRIES = 48
        private const val SHOW_RETRY_DELAY_MS = 100L
    }
}
