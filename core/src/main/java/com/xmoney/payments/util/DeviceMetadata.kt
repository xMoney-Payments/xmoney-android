package com.xmoney.payments.util

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import com.xmoney.payments.threeds.challengeUserAgent
import java.util.Locale
import java.util.TimeZone

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
object DeviceMetadata {
    private var preparedUserAgent: String? = null

    /** Cache the challenge WebView user agent before the first confirm request. */
    fun prepare(context: Context) {
        val raw = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        preparedUserAgent = if (raw.isNullOrBlank()) fallbackUserAgent() else challengeUserAgent(raw)
    }

    fun fields(context: Context): Map<String, String> {
        val metrics = context.resources.displayMetrics
        val tzOffsetMinutes = -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        return mapOf(
            "browserAcceptHeader" to "*/*",
            "browserLanguage" to browserLanguage(Locale.getDefault()),
            "browserColorDepth" to "24",
            "browserScreenHeight" to metrics.heightPixels.toString(),
            "browserScreenWidth" to metrics.widthPixels.toString(),
            "browserTimeZone" to tzOffsetMinutes.toString(),
            "browserJavaEnabled" to "false",
            "browserJavascriptEnabled" to "true",
            "browserUserAgent" to browserUserAgent(),
        )
    }

    fun httpHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to browserLanguage(Locale.getDefault()),
        "User-Agent" to browserUserAgent(),
    )

    /** Confirm copies this header into 3DS browserUserAgent. It must match the challenge WebView. */
    fun browserUserAgent(): String = preparedUserAgent ?: fallbackUserAgent()

    private fun fallbackUserAgent(): String =
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Mobile XMoneySDK/Android"

    /** The secure API accepts at most 8 characters. Prefer the full tag, then language-country, then language. */
    internal fun browserLanguage(locale: Locale): String {
        val tag = locale.toLanguageTag()
        if (tag.length <= 8) return tag
        val languageCountry = if (locale.country.isNotEmpty()) {
            "${locale.language}-${locale.country}"
        } else {
            locale.language
        }
        return if (languageCountry.length <= 8) languageCountry else locale.language.take(8)
    }
}
