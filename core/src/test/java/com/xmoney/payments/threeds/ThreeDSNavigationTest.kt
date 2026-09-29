package com.xmoney.payments.threeds

import android.content.Intent
import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreeDSNavigationTest {
    @Test
    fun httpsHopsStayInTheWebView() {
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("https://acs.example/challenge", true, false))
        assertEquals(
            ThreeDSHop.Proceed,
            ThreeDSNavigation.hop("https://merchant.example/return?status=ok", true, true),
        )
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("https://acs.example/method", false, false))
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("about:blank", true, false))
    }

    @Test
    fun nonHttpsMainFrameOpensOnlyWhenAnActivityResolves() {
        assertEquals(
            ThreeDSHop.OpenExternal,
            ThreeDSNavigation.hop("intent://pay#Intent;scheme=bank;end", true, true),
        )
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("bankapp://pay", true, false))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("bankapp://pay", false, true))
    }

    @Test
    fun insecureHopsStayInTheChallenge() {
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("http://acs.bank/challenge", true, true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("javascript:alert(1)", true, true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("data:text/html,hi", true, false))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("file:///tmp/x", true, false))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("http://acs.bank/challenge", false, true))
    }

    @Test
    fun abortedNavigationsDoNotFailTheChallenge() {
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_UNKNOWN,
                description = "net::ERR_ABORTED",
                requestMissingOrCancelled = false,
            ),
        )
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_UNKNOWN,
                description = null,
                requestMissingOrCancelled = true,
            ),
        )
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_CONNECT,
                description = null,
                requestMissingOrCancelled = true,
            ),
        )
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_UNKNOWN,
                description = null,
                requestMissingOrCancelled = false,
            ),
        )
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_UNSUPPORTED_SCHEME,
                description = "net::ERR_UNKNOWN_URL_SCHEME",
                requestMissingOrCancelled = false,
            ),
        )
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = false,
                errorCode = WebViewClient.ERROR_HOST_LOOKUP,
                description = "net::ERR_NAME_NOT_RESOLVED",
                requestMissingOrCancelled = false,
            ),
        )
        assertFalse(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_HOST_LOOKUP,
                description = "net::ERR_NAME_NOT_RESOLVED",
                requestMissingOrCancelled = false,
                isRedirect = true,
            ),
        )
    }

    @Test
    fun realMainFrameErrorsFailTheChallenge() {
        assertTrue(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_HOST_LOOKUP,
                description = "net::ERR_NAME_NOT_RESOLVED",
                requestMissingOrCancelled = false,
            ),
        )
        assertTrue(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_TIMEOUT,
                description = null,
                requestMissingOrCancelled = false,
            ),
        )
        assertTrue(
            ThreeDSNavigation.failsChallenge(
                isForMainFrame = true,
                errorCode = WebViewClient.ERROR_CONNECT,
                description = "net::ERR_CONNECTION_REFUSED",
                requestMissingOrCancelled = false,
            ),
        )
    }

    @Test
    fun externalIntentDoesNotKeepAnExplicitComponent() {
        val intent = ThreeDSNavigation.externalIntent(
            "intent://scan/#Intent;scheme=bank;package=com.bank.app;component=com.bank.app/.Trap;end",
        )
        assertTrue(intent != null)
        assertNull(intent!!.component)
        assertNull(intent.selector)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun externalIntentDropsGrantFlags() {
        val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        val intent = ThreeDSNavigation.externalIntent(
            "intent://pay/#Intent;scheme=bank;launchFlags=0x${grantFlags.toString(16)};end",
        )
        assertTrue(intent != null)
        assertEquals(0, intent!!.flags and grantFlags)
    }

    @Test
    fun externalIntentRejectsBrowserFileAndContentTargets() {
        assertNull(
            ThreeDSNavigation.externalIntent(
                "intent://evil.example/path#Intent;scheme=https;end",
            ),
        )
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=http;end"))
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=file;end"))
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=content;end"))
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=javascript;end"))
        assertTrue(ThreeDSNavigation.externalIntent("bankapp://pay") != null)
    }

    @Test
    fun stripsWebViewTokenFromUserAgent() {
        val raw = "Mozilla/5.0 (Linux; Android 14; Pixel; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36",
            challengeUserAgent(raw),
        )
    }
}
