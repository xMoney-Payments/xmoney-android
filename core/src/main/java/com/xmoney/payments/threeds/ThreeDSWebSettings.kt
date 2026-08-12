package com.xmoney.payments.threeds

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
}

internal fun destroyWebView(view: WebView?) {
    if (view == null) return
    runCatching {
        view.stopLoading()
        view.loadUrl("about:blank")
        (view.parent as? ViewGroup)?.removeView(view)
        view.webChromeClient = null
        view.destroy()
    }
}
