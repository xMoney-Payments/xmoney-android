package com.xmoney.payments.threeds
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

internal object ThreeDSUrlAllowlist {
    fun isAllowed(url: String): Boolean {
        if (url.equals("about:blank", ignoreCase = true)) return true
        val scheme = url.substringBefore(':', missingDelimiterValue = "")
            .lowercase()
            .trim()
        return scheme == "https" || scheme == "about"
    }
}
