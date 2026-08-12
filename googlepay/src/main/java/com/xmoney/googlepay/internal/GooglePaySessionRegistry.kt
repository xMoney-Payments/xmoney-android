package com.xmoney.googlepay.internal

import androidx.fragment.app.FragmentActivity
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.PaymentResult
import com.xmoney.googlepay.GooglePayEvent
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal object GooglePaySessionRegistry {
    data class Session(
        val onEvent: (GooglePayEvent) -> Unit = {},
        val onResult: (PaymentResult) -> Unit = {},
        val config: ResolvedPaymentConfig? = null,
        var host: WeakReference<FragmentActivity>? = null,
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

    fun finishHost(requestId: String?) {
        if (requestId.isNullOrBlank()) return
        sessions[requestId]?.host?.get()?.finish()
    }
}
