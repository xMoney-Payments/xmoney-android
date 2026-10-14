package com.xmoney.googlepay.internal

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.googlepay.GooglePayEvent
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal fun interface GooglePayCloseTarget {
    /** `true` if close started; `false` if refused (processing). */
    fun requestClose(): Boolean
}

internal interface GooglePayOrderTarget {
    suspend fun updateOrder(
        intent: PaymentIntent,
        onEvent: ((GooglePayEvent) -> Unit)? = null,
    )

    suspend fun updateOrder(
        onEvent: ((GooglePayEvent) -> Unit)? = null,
        createOrder: suspend () -> PaymentIntent,
    )
}

internal object GooglePaySessionRegistry {
    data class Session(
        var onEvent: (GooglePayEvent) -> Unit = {},
        val onResult: (PaymentResult) -> Unit = {},
        var config: ResolvedPaymentConfig? = null,
        var host: WeakReference<FragmentActivity>? = null,
        var closeTarget: WeakReference<GooglePayCloseTarget>? = null,
        var orderTarget: WeakReference<GooglePayOrderTarget>? = null,
        var isAvailable: Boolean = false,
        var isReady: Boolean = false,
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

    fun bindCloseTarget(requestId: String, target: GooglePayCloseTarget) {
        val session = sessions[requestId] ?: return
        session.closeTarget = WeakReference(target)
        if (session.closing) {
            target.requestClose()
        }
    }

    fun bindOrderTarget(requestId: String, target: GooglePayOrderTarget) {
        sessions[requestId]?.orderTarget = WeakReference(target)
    }

    /** Session exists and has not been asked to close. Includes a host still launching. */
    fun isActive(requestId: String?): Boolean {
        if (requestId.isNullOrBlank()) return false
        val session = sessions[requestId] ?: return false
        return !session.closing
    }

    fun finishHost(requestId: String?) {
        if (requestId.isNullOrBlank()) return
        val session = sessions[requestId] ?: return
        if (session.closing) return
        val target = session.closeTarget?.get()
        if (target == null) {
            session.closing = true
            return
        }
        if (target.requestClose()) session.closing = true
    }

    suspend fun updateOrder(
        requestId: String?,
        intent: PaymentIntent,
        onEvent: ((GooglePayEvent) -> Unit)? = null,
    ) {
        if (requestId.isNullOrBlank()) {
            throw IllegalStateException("Google Pay is not presented")
        }
        val target = sessions[requestId]?.orderTarget?.get()
            ?: throw IllegalStateException("Google Pay is not presented")
        target.updateOrder(intent, onEvent)
    }

    suspend fun updateOrder(
        requestId: String?,
        onEvent: ((GooglePayEvent) -> Unit)? = null,
        createOrder: suspend () -> PaymentIntent,
    ) {
        if (requestId.isNullOrBlank()) {
            throw IllegalStateException("Google Pay is not presented")
        }
        val target = sessions[requestId]?.orderTarget?.get()
            ?: throw IllegalStateException("Google Pay is not presented")
        target.updateOrder(onEvent, createOrder)
    }
}
