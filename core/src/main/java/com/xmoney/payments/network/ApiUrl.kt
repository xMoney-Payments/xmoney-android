package com.xmoney.payments.network

import androidx.annotation.RestrictTo
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Builds API URLs from a base origin and relative path, encoding each path segment. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object ApiUrl {
    fun encodePathSegment(value: String): String {
        val builder = StringBuilder(value.length)
        for (ch in value) {
            if (ch == '/' || ch == '?' || ch == '&' || ch == '#' || ch.code < 0x20 || ch.code >= 0x7F) {
                builder.append('%')
                builder.append("%02X".format(ch.code))
            } else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }

    fun make(
        base: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val httpUrl = base.toHttpUrl()
        val builder = httpUrl.newBuilder().encodedPath("/")
        path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
            builder.addPathSegment(segment)
        }
        query.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }
}
