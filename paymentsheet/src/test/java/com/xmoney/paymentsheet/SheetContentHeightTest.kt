package com.xmoney.paymentsheet

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.xmoney.paymentsheet.internal.sheetMaxHeightPx
import com.xmoney.paymentsheet.ui.sheetHeight
import com.xmoney.paymentsheet.ui.sheetMaxHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetContentHeightTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shortContentStaysBelowTheMax() {
        compose.setContent {
            Column(
                Modifier
                    .heightIn(max = sheetMaxHeight(1000.dp))
                    .testTag("sheet"),
            ) {
                Box(Modifier.fillMaxWidth().height(200.dp))
            }
        }
        val height = compose.onNodeWithTag("sheet").getBoundsInRoot().height
        assertEquals(200.dp, height)
        assertTrue(height < sheetMaxHeight(1000.dp))
        assertEquals(400.dp, sheetHeight(400.dp, 1000.dp))
    }

    @Test
    fun tallContentIsClampedToTheMax() {
        compose.setContent {
            Column(
                Modifier
                    .heightIn(max = sheetMaxHeight(300.dp))
                    .testTag("sheet"),
            ) {
                Box(Modifier.fillMaxWidth().height(800.dp))
            }
        }
        val height = compose.onNodeWithTag("sheet").getBoundsInRoot().height
        assertEquals(sheetMaxHeight(300.dp).value, height.value, 1f)
        assertEquals(950.dp, sheetHeight(2000.dp, 1000.dp))
        assertEquals(950, sheetMaxHeightPx(1000))
    }
}
