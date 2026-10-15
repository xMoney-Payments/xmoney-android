package com.xmoney.paymentelement

import android.content.Context
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.engine.PaymentSession
import com.xmoney.payments.model.OrderChecksum
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.network.HttpClient
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Answers session-token loads per order: succeed, fail, or hold until released. */
internal class FakeBackend {
    @Volatile var failAll = false
    private val failing = ConcurrentHashMap.newKeySet<String>()
    private val held = ConcurrentHashMap<String, CountDownLatch>()
    private val entered = ConcurrentHashMap<String, CountDownLatch>()

    fun fail(order: PaymentIntent) {
        failing += order.orderPayload
    }

    fun succeed(order: PaymentIntent) {
        failing -= order.orderPayload
    }

    /** The next load of [order] blocks until [release]. */
    fun hold(order: PaymentIntent) {
        held[order.orderPayload] = CountDownLatch(1)
        entered[order.orderPayload] = CountDownLatch(1)
    }

    fun isLoading(order: PaymentIntent): Boolean = entered[order.orderPayload]?.count == 0L

    /** Suspends, so the caller's own coroutines can start the load. */
    suspend fun awaitLoading(order: PaymentIntent) {
        withTimeout(5_000) { while (!isLoading(order)) delay(5) }
    }

    fun release(order: PaymentIntent) {
        held.remove(order.orderPayload)?.countDown()
    }

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }.orEmpty()
        val payload = held.keys.plus(failing).firstOrNull { body.contains(it) }
        if (payload != null) {
            entered[payload]?.countDown()
            held[payload]?.await(5, TimeUnit.SECONDS)
        }
        val fails = failAll || failing.any { body.contains(it) }
        val responseBody = when {
            fails -> """{"message":"nope"}"""
            request.url.encodedPath.contains("session-token") -> """{"token":"sess"}"""
            else -> "{}"
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (fails) 500 else 200)
            .message(if (fails) "fail" else "ok")
            .body(responseBody.toResponseBody())
            .build()
    }.build()

    fun session(context: Context) = PaymentSession(
        configuration = PaymentConfig(publicKey = "pk_test_x"),
        intent = order("session"),
        context = context,
        http = HttpClient(client),
    )

    companion object {
        fun order(suffix: String) = PaymentIntent(
            OrderPayload("payload-$suffix"),
            OrderChecksum("checksum-$suffix"),
        )
    }
}
