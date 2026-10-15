package com.xmoney.paymentelement.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xmoney.payments.config.CustomerDetailsConfig
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.customerdetails.CustomerDetailsVisibility
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.OrderPayloadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentFormCustomerDetailsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cardInputSurvivesThePersonalDetailsPageAndIsSubmitted() {
        var paid: CardInput? = null
        var details: Map<String, String>? = null
        compose.setContent {
            PaymentForm(
                config = config(),
                state = sheetState(),
                isProcessing = false,
                onPayCard = { card, payload ->
                    paid = card
                    details = payload
                },
                onSelectSaved = { _, _ -> },
                onGooglePay = {},
            )
        }

        compose.onNodeWithContentDescription("Card Number").performTextInput("4242424242424242")
        compose.onNodeWithContentDescription("Expiration Date").performTextInput("1230")
        compose.onNodeWithContentDescription("CVV").performTextInput("123")
        compose.onNodeWithContentDescription("Cardholder Name").performTextInput("Jane Doe")
        compose.onNodeWithText("Enter your personal details").performClick()
        compose.onNodeWithText("Email").performTextInput("a@b.co")
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithContentDescription("Card Number").assertTextContains("4242", substring = true)
        compose.onNodeWithText("Jane Doe").assertIsDisplayed()
        compose.onNodeWithText("Pay").performClick()

        assertEquals("4242424242424242", paid?.number)
        assertEquals("12", paid?.expiryMonth)
        assertEquals("30", paid?.expiryYear)
        assertEquals("123", paid?.cvv)
        assertEquals("Jane Doe", paid?.holderName)
        assertEquals("a@b.co", details?.get("email"))
    }

    @Test
    fun backFromPersonalDetailsReturnsToTheCardForm() {
        var paid: CardInput? = null
        compose.setContent {
            PaymentForm(
                config = config(),
                state = sheetState(),
                isProcessing = false,
                onPayCard = { card, _ -> paid = card },
                onSelectSaved = { _, _ -> },
                onGooglePay = {},
            )
        }

        compose.onNodeWithText("Enter your personal details").performClick()
        compose.onNodeWithText("Contact info").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        compose.onNodeWithText("Enter your personal details").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Contact info").fetchSemanticsNodes().size)
        compose.onNodeWithText("Pay").assertIsDisplayed()
        assertNull(paid)
    }

    private fun config() = ResolvedPaymentConfig(
        publicKey = "pk_test",
        orderPayload = "{}",
        orderChecksum = "checksum",
        customerDetails = CustomerDetailsConfig(card = true),
    )

    private fun sheetState() = SheetState(
        sessionToken = "token",
        orderInfo = OrderPayloadInfo(
            cardTransactionMode = null,
            isVerifyCard = false,
            amount = null,
            currency = null,
            externalOrderId = null,
            isRecurring = false,
        ),
        savedCards = emptyList(),
        googlePayAvailable = false,
        customerDetailsVisibility = CustomerDetailsVisibility.resolve(listOf("email")),
    )
}
