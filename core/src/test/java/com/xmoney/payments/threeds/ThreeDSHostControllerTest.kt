package com.xmoney.payments.threeds

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreeDSHostControllerTest {
    @Test
    fun defersShowUntilResumed() {
        runBlocking {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).create().start()
            val activity = controller.get()
            val host = ThreeDSHostController(activity) { "en" }
            val presented = async(Dispatchers.Unconfined) {
                host.presentThreeDS(
                    url = "https://acs.example/challenge",
                    returnUrlMatcher = { false },
                    formMethod = "GET",
                    params = emptyMap(),
                    onShown = {},
                )
            }
            ShadowLooper.idleMainLooper()
            assertNull(activity.supportFragmentManager.findFragmentByTag(ThreeDSDialog.TAG))

            controller.resume()
            ShadowLooper.idleMainLooper()
            activity.supportFragmentManager.executePendingTransactions()
            assertNotNull(activity.supportFragmentManager.findFragmentByTag(ThreeDSDialog.TAG))

            host.dismissThreeDS()
            ShadowLooper.idleMainLooper()
            withTimeout(2_000) { presented.await() }
        }
    }

    @Test
    fun showsImmediatelyWhenAlreadyResumed() {
        runBlocking {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java)
                .create()
                .start()
                .resume()
            val activity = controller.get()
            val host = ThreeDSHostController(activity) { "en" }
            val presented = async(Dispatchers.Unconfined) {
                host.presentThreeDS(
                    url = "https://acs.example/challenge",
                    returnUrlMatcher = { false },
                    formMethod = "GET",
                    params = emptyMap(),
                    onShown = {},
                )
            }
            ShadowLooper.idleMainLooper()
            activity.supportFragmentManager.executePendingTransactions()
            assertNotNull(activity.supportFragmentManager.findFragmentByTag(ThreeDSDialog.TAG))

            host.dismissThreeDS()
            ShadowLooper.idleMainLooper()
            withTimeout(2_000) { presented.await() }
        }
    }
}
