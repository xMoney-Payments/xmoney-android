package com.xmoney.payments.threeds

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

@SuppressLint("SetJavaScriptEnabled")
internal fun applySecureWebSettings(settings: WebSettings) {
    settings.javaScriptEnabled = true // required for ACS challenge pages
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(true)
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.setGeolocationEnabled(false)
    settings.mediaPlaybackRequiresUserGesture = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
}

/** Issuers break the challenge page when the user agent advertises a WebView. */
internal fun challengeUserAgent(defaultUserAgent: String): String =
    defaultUserAgent.replace(Regex("""\s*;\s*wv\b""", RegexOption.IGNORE_CASE), "")

/** The challenge page is a real WebView. Its user agent stays the WebView's, with `; wv` removed. */
internal fun applyChallengeUserAgent(settings: WebSettings, context: Context) {
    val raw = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull() ?: return
    settings.userAgentString = challengeUserAgent(raw)
}

/** ACS method iframes set cross-site cookies. WebView blocks those unless each instance opts in. */
internal fun acceptChallengeCookies(webView: WebView) {
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(webView, true)
}

/** Cookie writes are async. Flush before the next navigation or teardown so the ACS session is stored. */
internal fun flushChallengeCookies() {
    runCatching { CookieManager.getInstance().flush() }
}

internal fun destroyWebView(view: WebView?) {
    if (view == null) return
    flushChallengeCookies()
    runCatching {
        view.stopLoading()
        view.loadUrl("about:blank")
        (view.parent as? ViewGroup)?.removeView(view)
        view.webChromeClient = null
        view.destroy()
    }
}
