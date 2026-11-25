package com.xmoney.paymentelement.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.model.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedCardListHeightTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun threeCardsUseTheRealRowHeight() {
        setCards(3)
        val height = compose.onNodeWithTag("saved-card-list").getBoundsInRoot().height
        // Uncapped: every row is fully visible, even when a row grows past the 64 dp minimum.
        assertTrue(height >= SavedCardRowHeight * 3)
        assertTrue(kotlin.math.abs(height.value - (SavedCardRowHeight * MaxVisibleSavedCards).value) > 5f)
    }

    @Test
    fun moreThanThreeCardsPeekAtTheNextRow() {
        setCards(4)
        val height = compose.onNodeWithTag("saved-card-list").getBoundsInRoot().height
        assertEquals((SavedCardRowHeight * MaxVisibleSavedCards).value, height.value, 1f)
        assertTrue(height < SavedCardRowHeight * 4)
        assertTrue(compose.onAllNodesWithText("Bank 3", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun setCards(count: Int) {
        val cards = (0 until count).map { index ->
            SavedCard(
                id = "card-$index",
                cardNumber = "411111111111111$index",
                cardType = "visa",
                cardExpiryDate = "12/26",
                bankName = "Bank $index",
                cardBrand = "visa",
            )
        }
        val config = ResolvedPaymentConfig(publicKey = "pk", orderPayload = "p", orderChecksum = "c")
        val theme = CheckoutTheme.resolve(config, isDark = true)
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.width(400.dp)) {
                PaymentMethodsContainer(
                    config = config,
                    theme = theme,
                    cards = cards,
                    savedListExpanded = true,
                    selectedMethod = PaymentMethod.SavedCard,
                    selectedSavedId = cards.first().id,
                    isEditing = false,
                    pendingDeleteId = null,
                    deletingId = null,
                    enabled = true,
                    onExpandSavedCards = {},
                    onSelectSaved = {},
                    onSelectNewCard = {},
                    onToggleEdit = {},
                    onAskDelete = {},
                    onConfirmDelete = {},
                    onCancelDelete = {},
                    cardFormContent = {},
                )
            }
        }
        compose.waitForIdle()
    }
}
