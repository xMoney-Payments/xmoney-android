package com.xmoney.payments.threeds

import android.os.Bundle
import android.webkit.WebView
import java.net.URLEncoder

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
internal object ThreeDSFormBody {
    fun encode(params: Map<String, String>): ByteArray {
        if (params.isEmpty()) return ByteArray(0)
        return params.entries.joinToString("&") { (key, value) ->
            encodeFormValue(key) + "=" + encodeFormValue(value)
        }.toByteArray(Charsets.UTF_8)
    }

    fun toBundle(params: Map<String, String>): Bundle =
        Bundle(params.size).apply {
            params.forEach { (key, value) -> putString(key, value) }
        }

    fun fromBundle(bundle: Bundle?): Map<String, String> {
        if (bundle == null) return emptyMap()
        val params = LinkedHashMap<String, String>()
        for (key in bundle.keySet()) {
            params[key] = bundle.getString(key).orEmpty()
        }
        return params
    }

    fun load(webView: WebView, url: String, method: String, params: Map<String, String>) {
        if (method.equals("POST", ignoreCase = true)) {
            webView.postUrl(url, encode(params))
            return
        }
        webView.loadUrl(url)
    }

    private fun encodeFormValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
