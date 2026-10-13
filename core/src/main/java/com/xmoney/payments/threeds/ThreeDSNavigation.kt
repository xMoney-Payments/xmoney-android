package com.xmoney.payments.threeds

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

internal enum class ThreeDSHop {
    /** The WebView follows this hop and keeps the POST body and cookie session. */
    Proceed,
    /** A bank app, or its https fallback page, can take this URL. */
    OpenExternal,
    /** Drop this navigation. The challenge page stays up. */
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
        "market",
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

    private const val BROWSER_FALLBACK_EXTRA = "browser_fallback_url"

    /** Insecure hops in the middle of the flow are blocked, not failed, so a stray link cannot end the challenge. */
    fun hop(url: String, isForMainFrame: Boolean): ThreeDSHop {
        if (ThreeDSUrlAllowlist.isAllowed(url)) return ThreeDSHop.Proceed
        if (!isForMainFrame || isRejectedScheme(url)) return ThreeDSHop.Block
        val external = externalIntent(url) != null || browserFallbackUrl(url) != null
        return if (external) ThreeDSHop.OpenExternal else ThreeDSHop.Block
    }

    private fun isRejectedScheme(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "")
            .lowercase()
            .trim()
        return scheme in rejectedSchemes
    }

    /** On API 30+ the system refuses to open a browser for this intent. Android 11 hides other apps from `resolveActivity`. */
    fun externalIntent(url: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent? {
        val intent = parse(url) ?: return null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        intent.flags = intent.flags and URI_GRANT_FLAGS.inv()
        val nonBrowserOnly = sdkInt >= Build.VERSION_CODES.R
        if (nonBrowserOnly) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        }
        val scheme = intent.data?.scheme?.lowercase()?.trim().orEmpty()
        if (scheme == "https" && nonBrowserOnly && !intent.`package`.isNullOrBlank()) return intent
        if (scheme.isEmpty() || scheme in blockedExternalSchemes) return null
        return intent
    }

    /** An https link the bank app claims. Only from API 30, where the system can refuse browsers. */
    fun appLinkIntent(url: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent? {
        if (sdkInt < Build.VERSION_CODES.R) return null
        if (!ThreeDSUrlAllowlist.isHttpsChallenge(url)) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
    }

    /** The https page Chrome opens when no app handles an `intent:` link. */
    fun browserFallbackUrl(url: String): String? {
        if (!url.startsWith("intent:", ignoreCase = true)) return null
        val fallback = parse(url)?.getStringExtra(BROWSER_FALLBACK_EXTRA) ?: return null
        return fallback.takeIf { ThreeDSUrlAllowlist.isHttpsChallenge(it) }
    }

    /** Below API 30 only. Newer releases use [Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER] instead. */
    fun isDefaultHttpsBrowser(packageManager: PackageManager, resolvedPackage: String): Boolean {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .resolveActivity(packageManager)
            ?: return false
        return resolvedPackage == browser.packageName
    }

    private fun parse(url: String): Intent? =
        runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
}
