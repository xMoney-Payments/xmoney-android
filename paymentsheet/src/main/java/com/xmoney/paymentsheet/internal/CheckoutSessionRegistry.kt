package com.xmoney.paymentsheet.internal

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.PaymentSheetEvent
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal fun interface CheckoutCloseTarget {
    /** `true` if close started; `false` if refused (processing). */
    fun requestClose(): Boolean
}

internal object CheckoutSessionRegistry {
    data class Session(
        val onEvent: (PaymentSheetEvent) -> Unit = {},
        val onResult: ((PaymentResult) -> Unit)? = null,
        val onCardHolderVerification: ((CardHolderVerificationResult) -> Boolean)? = null,
        val config: ResolvedPaymentConfig? = null,
        var host: WeakReference<FragmentActivity>? = null,
        var closeTarget: WeakReference<CheckoutCloseTarget>? = null,
        var closing: Boolean = false,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun register(requestId: String, session: Session) {
        sessions[requestId] = session
    }

    fun get(requestId: String): Session? = sessions[requestId]

    fun remove(requestId: String) {
        sessions.remove(requestId)
    }

    fun bindHost(requestId: String, activity: FragmentActivity) {
        sessions[requestId]?.host = WeakReference(activity)
    }

    fun bindCloseTarget(requestId: String, target: CheckoutCloseTarget) {
        val session = sessions[requestId] ?: return
        session.closeTarget = WeakReference(target)
        if (session.closing) {
            target.requestClose()
        }
    }

    fun finishHost(requestId: String?) {
        replaceIdleHost(requestId)
    }

    /**
     * Closes the previous idle host, or marks it to close when it binds.
     * Returns `false` if that host is processing (caller must not present).
     */
    fun replaceIdleHost(previousId: String?): Boolean {
        if (previousId.isNullOrBlank()) return true
        val session = sessions[previousId] ?: return true
        if (session.closing) return true
        val target = session.closeTarget?.get()
        if (target == null) {
            session.closing = true
            return true
        }
        val accepted = target.requestClose()
        if (accepted) session.closing = true
        return accepted
    }
}
