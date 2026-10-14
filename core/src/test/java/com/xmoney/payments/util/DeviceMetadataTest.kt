package com.xmoney.payments.util

import android.os.Build
import android.webkit.WebSettings
import com.xmoney.payments.threeds.challengeUserAgent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceMetadataTest {
    private lateinit var previousLocale: Locale
    private lateinit var previousZone: TimeZone

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        previousZone = TimeZone.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ro-RO"))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Xiaomi")
        ReflectionHelpers.setStaticField(Build::class.java, "BRAND", "Redmi")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "25057RN09E")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "16")
        DeviceMetadata.prepare(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        TimeZone.setDefault(previousZone)
    }

    @Test
    fun fieldsMatchThreeDSBrowserData() {
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        metrics.widthPixels = 1080
        metrics.heightPixels = 2340

        val fields = DeviceMetadata.fields(context)

        assertEquals("*/*", fields["browserAcceptHeader"])
        assertEquals("ro-RO", fields["browserLanguage"])
        assertTrue(fields["browserLanguage"]!!.length <= 8)
        assertEquals("24", fields["browserColorDepth"])
        assertEquals("2340", fields["browserScreenHeight"])
        assertEquals("1080", fields["browserScreenWidth"])
        assertEquals("-180", fields["browserTimeZone"])
        assertEquals("false", fields["browserJavaEnabled"])
        assertEquals("true", fields["browserJavascriptEnabled"])
        val expectedAgent = challengeUserAgent(WebSettings.getDefaultUserAgent(context))
        assertEquals(expectedAgent, fields["browserUserAgent"])
        assertFalse(Regex(""";\s*wv\b""", RegexOption.IGNORE_CASE).containsMatchIn(expectedAgent))
    }

    @Test
    fun browserLanguageStaysWithinEightCharacters() {
        val language = DeviceMetadata.browserLanguage(Locale.forLanguageTag("zh-Hans-CN"))
        assertEquals("zh-CN", language)
        assertTrue(language.length <= 8)
    }

    @Test
    fun httpHeadersMatchTheBrowserFields() {
        val headers = DeviceMetadata.httpHeaders()
        assertEquals("*/*", headers["Accept"])
        assertEquals("ro-RO", headers["Accept-Language"])
        assertEquals(DeviceMetadata.browserUserAgent(), headers["User-Agent"])
        assertFalse(Regex(""";\s*wv\b""", RegexOption.IGNORE_CASE).containsMatchIn(headers["User-Agent"]!!))
    }
}
