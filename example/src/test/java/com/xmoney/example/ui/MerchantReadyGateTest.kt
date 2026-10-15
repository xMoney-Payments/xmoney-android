package com.xmoney.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MerchantReadyGateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun closedGateHidesCheckoutAndAnnouncesLoadingOnce() {
        compose.setContent {
            MaterialTheme {
                MerchantReadyGate(ready = false, message = "Preparing checkout") {
                    Text("Card number")
                }
            }
        }
        compose.onAllNodesWithText("Card number").assertCountEquals(0)
        compose.onNodeWithContentDescription("Preparing checkout").assertExists()
    }
}
