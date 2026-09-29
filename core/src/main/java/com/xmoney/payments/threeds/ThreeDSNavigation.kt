package com.xmoney.payments.threeds

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebViewClient

internal enum class ThreeDSHop {
    /** The WebView follows this hop and keeps the POST body and cookie session. */
    Proceed,
    /** A bank app can open this URL. Show the waiting screen and keep polling. */
    OpenExternal,
    /** The challenge document itself is not https. Fail after a completion check. */
    Reject,
    /** Drop this navigation. The challenge stays up. */
    Block,
}

internal object ThreeDSNavigation {
    private val rejectedSchemes = setOf(
        "http",
        "javascript",
        "data",
        "file",
        "blob",
        "tel",
        "sms",
        "mailto",
    )

    /** Parsed targets that must not leave the challenge, including every [rejectedSchemes] entry. */
    private val blockedExternalSchemes = rejectedSchemes + setOf(
        "https",
        "content",
        "intent",
        "about",
    )

    private const val URI_GRANT_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    fun hop(url: String, isForMainFrame: Boolean, canOpenExternally: Boolean): ThreeDSHop {
        if (ThreeDSUrlAllowlist.isAllowed(url)) return ThreeDSHop.Proceed
        // A later insecure hop stays blocked so the current challenge page remains.
        // Reject is only for the initial non-https document.
        if (!isForMainFrame || isRejectedScheme(url)) return ThreeDSHop.Block
        return if (canOpenExternally) ThreeDSHop.OpenExternal else ThreeDSHop.Block
    }

    private fun isRejectedScheme(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "")
            .lowercase()
            .trim()
        return scheme in rejectedSchemes
    }

    /**
     * A redirect that replaces the challenge POST is reported as [WebViewClient.ERROR_UNKNOWN],
     * often with a request still present and no `ERR_ABORTED` text. Closing on that removes the
     * challenge before it is visible. Unsupported schemes are navigations this client already
     * cancelled. A real main-frame failure still closes.
     */
    fun failsChallenge(
        isForMainFrame: Boolean,
        errorCode: Int,
        description: CharSequence?,
        requestMissingOrCancelled: Boolean,
        isRedirect: Boolean = false,
    ): Boolean {
        if (!isForMainFrame || isRedirect) return false
        if (isAbortedNavigation(errorCode, description, requestMissingOrCancelled)) return false
        return true
    }

    fun externalIntent(url: String): Intent? {
        val intent = runCatching {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        intent.flags = intent.flags and URI_GRANT_FLAGS.inv()
        val scheme = intent.data?.scheme?.lowercase()?.trim().orEmpty()
        if (scheme.isEmpty() || scheme in blockedExternalSchemes) return null
        return intent
    }

    fun isDefaultHttpsBrowser(packageManager: PackageManager, resolvedPackage: String): Boolean {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .resolveActivity(packageManager)
            ?: return false
        return resolvedPackage == browser.packageName
    }

    private fun isAbortedNavigation(
        errorCode: Int,
        description: CharSequence?,
        requestMissingOrCancelled: Boolean,
    ): Boolean {
        val text = description?.toString().orEmpty()
        if (text.contains("ERR_ABORTED", ignoreCase = true)) return true
        if (errorCode == WebViewClient.ERROR_UNKNOWN) return true
        if (errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME) return true
        if (!requestMissingOrCancelled) return false
        return errorCode == WebViewClient.ERROR_CONNECT
    }
}
