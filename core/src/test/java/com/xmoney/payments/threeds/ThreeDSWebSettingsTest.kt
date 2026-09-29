package com.xmoney.payments.threeds

import android.app.Activity
import android.webkit.CookieManager
import android.webkit.WebView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreeDSWebSettingsTest {
    @Test
    fun acceptChallengeCookiesEnablesFirstAndThirdParty() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = WebView(activity)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(false)
        try {
            acceptChallengeCookies(webView)
            assertTrue(cookies.acceptCookie())
            flushChallengeCookies()
        } finally {
            cookies.setAcceptCookie(true)
        }
    }

    @Test
    fun challengeUserAgentStaysOnTheWebView() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = WebView(activity)
        applyChallengeUserAgent(webView.settings, activity)
        val userAgent = webView.settings.userAgentString.orEmpty()
        assertFalse(userAgent.contains("XMoneySDK"))
        assertFalse(Regex(""";\s*wv\b""", RegexOption.IGNORE_CASE).containsMatchIn(userAgent))
    }
}
