package com.xmoney.payments.threeds

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreeDSNavigationTest {
    @Test
    fun httpsHopsStayInTheWebView() {
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("https://acs.example/challenge", true))
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("https://merchant.example/return?status=ok", true))
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("https://acs.example/method", false))
        assertEquals(ThreeDSHop.Proceed, ThreeDSNavigation.hop("about:blank", true))
    }

    @Test
    fun mainFrameAppLinksOpenExternally() {
        assertEquals(ThreeDSHop.OpenExternal, ThreeDSNavigation.hop("bankapp://pay", true))
        assertEquals(ThreeDSHop.OpenExternal, ThreeDSNavigation.hop("intent://pay#Intent;scheme=bank;end", true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("bankapp://pay", false))
    }

    @Test
    fun intentWithOnlyAnHttpsFallbackOpensExternally() {
        val url = "intent://pay#Intent;scheme=https;S.browser_fallback_url=https%3A%2F%2Facs.bank%2Fweb;end"
        assertEquals(ThreeDSHop.OpenExternal, ThreeDSNavigation.hop(url, true))
    }

    @Test
    fun insecureHopsStayInTheChallenge() {
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("http://acs.bank/challenge", true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("javascript:alert(1)", true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("data:text/html,hi", true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("file:///tmp/x", true))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("http://acs.bank/challenge", false))
        assertEquals(ThreeDSHop.Block, ThreeDSNavigation.hop("market://details?id=com.bank.app", true))
    }

    @Test
    fun browserFallbackMustBeHttps() {
        assertEquals(
            "https://acs.bank/web",
            ThreeDSNavigation.browserFallbackUrl(
                "intent://pay#Intent;scheme=bank;S.browser_fallback_url=https%3A%2F%2Facs.bank%2Fweb;end",
            ),
        )
        assertNull(
            ThreeDSNavigation.browserFallbackUrl(
                "intent://pay#Intent;scheme=bank;S.browser_fallback_url=http%3A%2F%2Facs.bank%2Fweb;end",
            ),
        )
        assertNull(
            ThreeDSNavigation.browserFallbackUrl(
                "intent://pay#Intent;scheme=bank;S.browser_fallback_url=javascript%3Aalert(1);end",
            ),
        )
        assertNull(ThreeDSNavigation.browserFallbackUrl("bankapp://pay?browser_fallback_url=https://x"))
    }

    @Test
    fun externalIntentSkipsBrowsersOnAndroid11() {
        val modern = ThreeDSNavigation.externalIntent("bankapp://pay", sdkInt = 30)!!
        assertTrue(modern.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        val legacy = ThreeDSNavigation.externalIntent("bankapp://pay", sdkInt = 29)!!
        assertEquals(0, legacy.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
    }

    @Test
    fun httpsIntentNamingABankAppOpensOnAndroid11() {
        val url = "intent://bank.example/pay#Intent;scheme=https;package=com.bank.app;end"
        val intent = ThreeDSNavigation.externalIntent(url, sdkInt = 30)!!
        assertEquals("com.bank.app", intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        assertNull(ThreeDSNavigation.externalIntent(url, sdkInt = 29))
        assertNull(
            ThreeDSNavigation.externalIntent("intent://bank.example/pay#Intent;scheme=https;end", sdkInt = 30),
        )
    }

    @Test
    fun appLinkIntentRefusesBrowsers() {
        val intent = ThreeDSNavigation.appLinkIntent("https://bank.example/approve", sdkInt = 30)!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        assertNull(ThreeDSNavigation.appLinkIntent("https://bank.example/approve", sdkInt = 29))
        assertNull(ThreeDSNavigation.appLinkIntent("http://bank.example/approve", sdkInt = 30))
    }

    @Test
    fun cardNetworkLinksStayInTheChallenge() {
        assertNull(ThreeDSNavigation.appLinkIntent("https://www.visa.com/help", sdkInt = 30))
        assertNull(ThreeDSNavigation.appLinkIntent("https://mastercard.com/securecode", sdkInt = 30))
        assertNull(ThreeDSNavigation.appLinkIntent("https://WWW.MasterCard.com./x", sdkInt = 30))
        assertTrue(ThreeDSNavigation.appLinkIntent("https://bank.example/approve", sdkInt = 30) != null)
        assertTrue(ThreeDSNavigation.appLinkIntent("https://notvisa.com/approve", sdkInt = 30) != null)
    }

    @Test
    fun ownAppClaimIsDetected() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context.packageName, "com.merchant.ReturnActivity")
        val shadow = shadowOf(context.packageManager)
        shadow.addActivityIfNotPresent(component)
        shadow.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
                addDataAuthority("merchant.example", null)
            },
        )
        val own = ThreeDSNavigation.appLinkIntent("https://merchant.example/return", sdkInt = 30)!!
        val other = ThreeDSNavigation.appLinkIntent("https://bank.example/approve", sdkInt = 30)!!
        assertTrue(ThreeDSNavigation.claimedByOwnApp(context.packageManager, context.packageName, own))
        assertFalse(ThreeDSNavigation.claimedByOwnApp(context.packageManager, context.packageName, other))
    }

    @Test
    fun externalIntentRejectsMarket() {
        assertNull(ThreeDSNavigation.externalIntent("market://details?id=com.bank.app"))
        assertNull(ThreeDSNavigation.externalIntent("intent://details?id=x#Intent;scheme=market;end"))
    }

    @Test
    fun externalIntentDoesNotKeepAnExplicitComponent() {
        val intent = ThreeDSNavigation.externalIntent(
            "intent://scan/#Intent;scheme=bank;package=com.bank.app;component=com.bank.app/.Trap;end",
        )
        assertTrue(intent != null)
        assertNull(intent!!.component)
        assertNull(intent.selector)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun externalIntentDropsGrantFlags() {
        val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        val intent = ThreeDSNavigation.externalIntent(
            "intent://pay/#Intent;scheme=bank;launchFlags=0x${grantFlags.toString(16)};end",
        )
        assertTrue(intent != null)
        assertEquals(0, intent!!.flags and grantFlags)
    }

    @Test
    fun externalIntentRejectsBrowserFileAndContentTargets() {
        assertNull(
            ThreeDSNavigation.externalIntent(
                "intent://evil.example/path#Intent;scheme=https;end",
            ),
        )
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=http;end"))
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=file;end"))
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=content;end"))
        assertNull(ThreeDSNavigation.externalIntent("intent://evil#Intent;scheme=javascript;end"))
        assertTrue(ThreeDSNavigation.externalIntent("bankapp://pay") != null)
    }

    @Test
    fun stripsWebViewTokenFromUserAgent() {
        val raw = "Mozilla/5.0 (Linux; Android 14; Pixel; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36",
            challengeUserAgent(raw),
        )
    }
}
