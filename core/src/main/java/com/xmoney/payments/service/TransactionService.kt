package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.Transaction
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient

import kotlinx.coroutines.delay
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class TransactionService(private val http: HttpClient, private val env: PaymentEnvironment) {
    suspend fun getTransaction(id: String, sessionToken: String): Transaction {
        val url = ApiUrl.make(env.apiNextBaseURL, "${SdkConstants.TRANSACTIONS_PATH}/$id")
        return Transaction.fromApiMap(http.getJson(url, sessionToken))
    }

    suspend fun poll(
        transactionId: String,
        sessionToken: String,
        intervalMs: Long = 2000,
        timeoutMs: Long = 600_000,
    ): Transaction {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val tx = getTransaction(transactionId, sessionToken)
            if (tx.isComplete) return tx
            delay(intervalMs)
        }
        throw PaymentError.PollTimeout()
    }
}
