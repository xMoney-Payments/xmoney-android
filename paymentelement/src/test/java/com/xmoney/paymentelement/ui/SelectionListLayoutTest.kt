package com.xmoney.paymentelement.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.ResolvedPaymentConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionListLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val options = listOf(
        PickerOption("PT", "Portugal"),
        PickerOption("AT", "Austria"),
        PickerOption("BE", "Belgium"),
    )

    @Test
    fun matchesFigmaCountryPicker() {
        var picked: String? = null
        var closed = false
        setContent(onSelect = { picked = it }, onClose = { closed = true })

        val close = compose.onNodeWithContentDescription("Cancel")
        val closeBounds = close.getBoundsInRoot()
        val titleBounds = compose.onNode(hasText("Country") and isHeading()).getBoundsInRoot()
        assertEquals(6f, closeBounds.left.value, 0.5f)
        assertEquals(44f, closeBounds.width.value, 0.5f)
        assertEquals(closeBounds.right.value + 12f, titleBounds.left.value, 0.5f)
        assertEquals(
            (closeBounds.top.value + closeBounds.bottom.value) / 2,
            (titleBounds.top.value + titleBounds.bottom.value) / 2,
            1f,
        )
        close.performClick()
        assertEquals(true, closed)

        val portugal = compose.onNode(hasText("Portugal") and isRadio(), useUnmergedTree = false)
        portugal.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNode(hasText("Austria") and isRadio())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))

        val first = portugal.getBoundsInRoot()
        val second = compose.onNode(hasText("Austria") and isRadio()).getBoundsInRoot()
        assertEquals(48.dp, first.height)
        assertEquals(12.dp, second.top - first.bottom)

        compose.onNode(hasSetTextAction()).performTextInput("bel")
        compose.onNodeWithText("Austria").assertDoesNotExist()
        compose.onNodeWithText("Belgium").performClick()
        assertEquals("BE", picked)
    }

    private fun setContent(onSelect: (String) -> Unit, onClose: () -> Unit) {
        val config = ResolvedPaymentConfig(publicKey = "pk", orderPayload = "p", orderChecksum = "c")
        compose.setContent {
            val theme = CheckoutTheme.resolve(config, false)
            Column {
                SelectionListHeader(
                    title = "Country",
                    theme = theme,
                    locale = "en",
                    enabled = true,
                    onClose = onClose,
                )
                SelectionList(
                    title = "Country",
                    options = options,
                    selected = "PT",
                    theme = theme,
                    modifier = Modifier.height(400.dp),
                    onSelect = onSelect,
                )
            }
        }
    }

    private fun isRadio() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private fun hasSetTextAction() =
        SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetText)
}
