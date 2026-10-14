package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.Transaction
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient
import com.xmoney.payments.network.HttpStatusException

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class TransactionService(private val http: HttpClient, private val env: PaymentEnvironment) {
    suspend fun getTransaction(id: String, sessionToken: String): Transaction =
        try {
            fetch(id, sessionToken)
        } catch (failure: HttpStatusException) {
            throw failure.error
        }

    /**
     * Transient network and server errors are retried until the deadline. The shopper may be in a
     * bank app while this runs. Before any error is reported, including the timeout, the transaction
     * is fetched once more.
     */
    suspend fun poll(
        transactionId: String,
        sessionToken: String,
        intervalMs: Long = 2000,
        timeoutMs: Long = 600_000,
    ): Transaction {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val tx = fetch(transactionId, sessionToken)
                if (tx.isComplete) return tx
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isTransient(e)) {
                    return finalCheck(transactionId, sessionToken, surfaced(e))
                }
            }
            delay(intervalMs)
        }
        return finalCheck(transactionId, sessionToken, PaymentError.PollTimeout())
    }

    private suspend fun finalCheck(id: String, sessionToken: String, error: Exception): Transaction {
        currentCoroutineContext().ensureActive()
        val tx = try {
            fetch(id, sessionToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (tx != null && tx.isComplete) return tx
        throw error
    }

    private suspend fun fetch(id: String, sessionToken: String): Transaction {
        val url = ApiUrl.make(env.apiNextBaseURL, "${SdkConstants.TRANSACTIONS_PATH}/$id")
        return Transaction.fromApiMap(http.getJson(url, sessionToken, exposingStatus = true))
    }

    internal companion object {
        fun isTransient(error: Throwable): Boolean = when (error) {
            is HttpStatusException -> error.statusCode == 429 || error.statusCode in 500..599
            is IOException -> true
            else -> false
        }

        private fun surfaced(error: Exception): Exception =
            (error as? HttpStatusException)?.error ?: error
    }
}
