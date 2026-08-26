package com.xmoney.payments.util

import android.content.Context
import android.webkit.WebSettings
import java.util.Locale
import java.util.TimeZone

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
object DeviceMetadata {
    fun fields(context: Context): Map<String, String> {
        val metrics = context.resources.displayMetrics
        val tzOffsetMinutes = -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        return mapOf(
            "browserLanguage" to Locale.getDefault().toLanguageTag(),
            "browserUserAgent" to userAgent(context),
            "browserColorDepth" to "24",
            "browserScreenHeight" to metrics.heightPixels.toString(),
            "browserScreenWidth" to metrics.widthPixels.toString(),
            "browserTimeZone" to tzOffsetMinutes.toString(),
            "browserJavaEnabled" to "false",
            "browserJavascriptEnabled" to "true",
        )
    }

    fun httpHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to Locale.getDefault().toLanguageTag(),
        "User-Agent" to fallbackUserAgent(),
    )

    fun userAgent(context: Context): String =
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrElse { fallbackUserAgent() }

    private fun fallbackUserAgent(): String {
        val version = android.os.Build.VERSION.RELEASE
        val model = android.os.Build.MODEL
        return "Mozilla/5.0 (Linux; Android $version; $model) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Mobile XMoneySDK/Android"
    }
}
