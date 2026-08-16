package com.xmoney.payments.network

import com.xmoney.payments.model.PaymentError
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HttpHygieneTest {
    @Test
    fun apiUrlEncodesPathSegments() {
        val url = ApiUrl.make(
            "https://api-next.xmoney.com",
            "api/v1/inline-checkout/cards/id/with?slash",
        )
        assertTrue(url.contains("cards"))
        assertTrue(url.contains("id"))
        assertTrue(url.contains("with%3Fslash") || url.contains("with%3fslash"))
        assertFalse(url.contains("cards/id/with?slash"))
    }

    @Test
    fun apiUrlEncodePathSegmentEscapesReserved() {
        assertEquals("a%2Fb", ApiUrl.encodePathSegment("a/b"))
        assertEquals("a%3Fb", ApiUrl.encodePathSegment("a?b"))
    }

    @Test
    fun multipartRejectsCRLFAndBoundary() {
        try {
            HttpClient.sanitizedMultipartToken("ok\r\n", "xmoney-bound", "value")
            fail("expected PaymentError.Network")
        } catch (e: PaymentError.Network) {
            assertTrue(e.message.contains("Invalid multipart field value"))
        }
        try {
            HttpClient.sanitizedMultipartToken("xmoney-bound", "xmoney-bound", "name")
            fail("expected PaymentError.Network")
        } catch (e: PaymentError.Network) {
            assertTrue(e.message.contains("Invalid multipart field name"))
        }
        assertEquals("ok", HttpClient.sanitizedMultipartToken("ok", "xmoney-bound", "value"))
    }

    @Test
    fun deleteNon2xx_throwsNetwork() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()
        try {
            HttpClient(client).delete("https://api-next.xmoney.com/api/v1/inline-checkout/cards/abc", "token")
            fail("expected PaymentError.Network")
        } catch (e: PaymentError.Network) {
            assertTrue(e.message.isNotEmpty())
        }
    }
}
