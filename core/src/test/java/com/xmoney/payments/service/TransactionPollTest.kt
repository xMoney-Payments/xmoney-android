package com.xmoney.payments.service

import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.network.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionPollTest {
    private sealed class Reply {
        data class Status(val code: Int, val body: String = "{}") : Reply()
        data object Offline : Reply()
    }

    private var requests = 0

    private fun service(vararg replies: Reply): TransactionService {
        val queue = ArrayDeque(replies.toList())
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests++
                when (val reply = queue.removeFirstOrNull() ?: replies.last()) {
                    Reply.Offline -> throw IOException("offline")
                    is Reply.Status -> Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(reply.code)
                        .message("status")
                        .body(reply.body.toResponseBody(null))
                        .build()
                }
            }
            .build()
        return TransactionService(HttpClient(client), PaymentEnvironment.from("pk_test_x")!!)
    }

    private val pending = Reply.Status(200, """{"transactionId":"1","transactionStatus":"in-progress"}""")
    private val complete = Reply.Status(200, """{"transactionId":"1","transactionStatus":"complete-ok"}""")

    @Test
    fun serverErrorIsRetried() = runBlocking {
        val tx = service(Reply.Status(500), Reply.Status(429), complete).poll("1", "t", intervalMs = 1)
        assertEquals("complete-ok", tx.status)
        assertEquals(3, requests)
    }

    @Test
    fun networkErrorIsRetried() = runBlocking {
        val tx = service(Reply.Offline, complete).poll("1", "t", intervalMs = 1)
        assertEquals("complete-ok", tx.status)
    }

    @Test
    fun clientErrorThrowsAfterAFinalCheck() = runBlocking {
        try {
            service(Reply.Status(401), pending).poll("1", "t", intervalMs = 1)
            fail("expected PaymentError.Network")
        } catch (_: PaymentError.Network) {
        }
        assertEquals(2, requests)
    }

    @Test
    fun clientErrorReturnsATransactionThatCompletedMeanwhile() = runBlocking {
        val tx = service(Reply.Status(401), complete).poll("1", "t", intervalMs = 1)
        assertEquals("complete-ok", tx.status)
    }

    @Test
    fun timeoutChecksOnceMore() = runBlocking {
        val tx = service(complete).poll("1", "t", intervalMs = 1, timeoutMs = 0)
        assertEquals("complete-ok", tx.status)
        assertEquals(1, requests)
    }

    @Test
    fun timeoutWithoutCompletionThrowsPollTimeout() = runBlocking {
        try {
            service(pending).poll("1", "t", intervalMs = 1, timeoutMs = 20)
            fail("expected PaymentError.PollTimeout")
        } catch (_: PaymentError.PollTimeout) {
        }
    }

    @Test
    fun getTransactionKeepsTheNetworkError() = runBlocking {
        try {
            service(Reply.Status(503)).getTransaction("1", "t")
            fail("expected PaymentError.Network")
        } catch (_: PaymentError.Network) {
        }
    }
}
