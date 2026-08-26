package com.xmoney.payments.service

import com.xmoney.payments.model.ConfirmPaymentResponse
import com.xmoney.payments.network.HttpClient
import com.xmoney.payments.threeds.ThreeDSUrlAllowlist
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.model.PaymentSubmissionResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentServiceParseTest {
    private val service = PaymentService(
        HttpClient.shared(),
        PaymentEnvironment.from("pk_test_x")!!,
        RuntimeEnvironment.getApplication(),
    )

    @Test
    fun parseMatchesCheckoutSdkGooglePayPendingRedirect() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 200,
                  "status": "Request Completed",
                  "data": {
                    "transaction": {
                      "transactionId": 726612,
                      "status": "pending-redirect",
                      "responseStatus": "3d-pending",
                      "is3d": 1,
                      "methodType": "digitalWallet",
                      "methodName": "googlePay",
                      "redirectUrl": "https://xmoney.uat.radarpayment.online/payment/rest/getwhitepageurl.do?mdOrder=abc",
                      "redirectParams": { "paReq": "White page paReq", "termUrl": "White page termUrl" },
                      "redirectMethod": "POST"
                    },
                    "orderRequest": {
                      "processing": {
                        "backUrl": "https://merchant.example/inline-checkout"
                      }
                    },
                    "result": "encoded-result",
                    "threeDSFlowUrl": "https://secure-stage.xmoney.com/secure/three-d-s-flow?cartId=abc"
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        submission as PaymentSubmissionResult.Needs3DS
        assertEquals(
            "https://secure-stage.xmoney.com/secure/three-d-s-flow?cartId=abc",
            submission.url,
        )
        assertEquals("GET", submission.formMethod)
        assertTrue(submission.params.isEmpty())
        assertEquals("726612", parsed.transactionId)
    }

    @Test
    fun parseAcceptsHttpsThreeDSURL() {
        val parsed = service.parse(response("https://acs.example/challenge"))
        assertTrue(parsed.submission is PaymentSubmissionResult.Needs3DS)
        assertEquals("tx-1", parsed.transactionId)
    }

    @Test
    fun parseAcceptsWalletRedirectUrlWhenBackUrlPresentWithoutFlags() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 200,
                  "status": "ok",
                  "data": {
                    "orderRequest": { "processing": { "backUrl": "https://merchant.example/return" } },
                    "transaction": {
                      "transactionId": "tx-gpay",
                      "status": "pending",
                      "redirectUrl": "https://acs.example/wallet-challenge"
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        assertEquals(
            "https://acs.example/wallet-challenge",
            (submission as PaymentSubmissionResult.Needs3DS).url,
        )
        assertEquals("tx-gpay", parsed.transactionId)
    }

    @Test
    fun parseAcceptsTopLevelRedirectUrlStringWithBackUrl() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 201,
                  "status": "ok",
                  "data": {
                    "redirectUrl": "https://acs.example/wallet-challenge",
                    "orderRequest": { "processing": { "backUrl": "https://merchant.example/return" } },
                    "transaction": {
                      "transactionId": "tx-gpay",
                      "status": "pending"
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        assertEquals(
            "https://acs.example/wallet-challenge",
            (submission as PaymentSubmissionResult.Needs3DS).url,
        )
        assertEquals("tx-gpay", parsed.transactionId)
    }

    @Test
    fun parseAcceptsWalletThreeDSWhenBackUrlAlsoPresent() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 200,
                  "status": "ok",
                  "data": {
                    "threeDSFlowUrl": "https://acs.example/wallet-challenge",
                    "orderRequest": { "processing": { "backUrl": "https://merchant.example/return" } },
                    "transaction": {
                      "transactionId": "tx-gpay",
                      "status": "pending",
                      "responseStatus": "3d-pending"
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        assertEquals(
            "https://acs.example/wallet-challenge",
            (submission as PaymentSubmissionResult.Needs3DS).url,
        )
        assertEquals("tx-gpay", parsed.transactionId)
    }

    @Test
    fun parseRejectsNonHttpsThreeDSURL() {
        try {
            service.parse(response("http://acs.example/challenge"))
            fail("expected THREE_DS_ERROR")
        } catch (e: PaymentError.ThreeDS) {
            assertEquals("THREE_DS_ERROR", e.code)
        }
        assertFalseHttps("http://acs.example/challenge")
    }

    @Test
    fun parseAcceptsGooglePay201RedirectAsNeeds3DS() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 201,
                  "message": "Created",
                  "data": {
                    "orderId": 99,
                    "transactionId": 4242,
                    "is3d": 1,
                    "isRedirect": true,
                    "redirect": {
                      "url": "https://secure.xmoney.com/acs20",
                      "formMethod": "POST",
                      "params": {
                        "PaReq": "PAREQvalue",
                        "MD": "md-1",
                        "TermsUrl": "https://merchant.example/3ds/return"
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        submission as PaymentSubmissionResult.Needs3DS
        assertEquals("https://secure.xmoney.com/acs20", submission.url)
        assertEquals("POST", submission.formMethod)
        assertEquals("PAREQvalue", submission.params["PaReq"])
        assertEquals("md-1", submission.params["MD"])
        assertEquals("https://merchant.example/3ds/return", submission.params["TermsUrl"])
        assertEquals("4242", parsed.transactionId)
    }

    @Test
    fun parseAcceptsString201CodeAsNeeds3DS() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": "201",
                  "message": "Created",
                  "data": {
                    "transactionId": 88,
                    "is3d": 1,
                    "isRedirect": true,
                    "redirect": {
                      "url": "https://secure.xmoney.com/acs20",
                      "formMethod": "POST",
                      "params": { "PaReq": "p" }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        submission as PaymentSubmissionResult.Needs3DS
        assertEquals("https://secure.xmoney.com/acs20", submission.url)
        assertEquals("POST", submission.formMethod)
        assertEquals("88", parsed.transactionId)
    }

    @Test
    fun parseAcceptsMissingCodeWhenRedirectPresent() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "message": "Created",
                  "data": {
                    "transactionId": 9,
                    "is3d": 1,
                    "isRedirect": true,
                    "redirect": {
                      "url": "https://secure.xmoney.com/acs20",
                      "formMethod": "POST",
                      "params": { "MD": "md" }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val parsed = service.parse(ConfirmPaymentResponse.fromApiMap(map))
        val submission = parsed.submission
        assertTrue(submission is PaymentSubmissionResult.Needs3DS)
        submission as PaymentSubmissionResult.Needs3DS
        assertEquals("https://secure.xmoney.com/acs20", submission.url)
        assertEquals("POST", submission.formMethod)
        assertEquals("md", submission.params["MD"])
        assertEquals("9", parsed.transactionId)
    }

    @Test
    fun parse201WithoutChallengeUrlThrowsThreeDS() {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 201,
                  "message": "Created",
                  "data": {
                    "transactionId": 7,
                    "is3d": 1,
                    "isRedirect": true
                  }
                }
                """.trimIndent(),
            ),
        )
        try {
            service.parse(ConfirmPaymentResponse.fromApiMap(map))
            fail("expected THREE_DS_ERROR")
        } catch (e: PaymentError.ThreeDS) {
            assertEquals("THREE_DS_ERROR", e.code)
        }
    }

    private fun assertFalseHttps(url: String) {
        assertTrue(!ThreeDSUrlAllowlist.isHttpsChallenge(url))
    }

    private fun response(threeDSUrl: String): ConfirmPaymentResponse {
        val map = HttpClient.jsonToMap(
            JSONObject(
                """
                {
                  "code": 200,
                  "status": "ok",
                  "data": {
                    "threeDSFlowUrl": "$threeDSUrl",
                    "transaction": {
                      "transactionId": "tx-1",
                      "status": "pending-redirect",
                      "responseStatus": "3d-pending"
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        return ConfirmPaymentResponse.fromApiMap(map)
    }
}
