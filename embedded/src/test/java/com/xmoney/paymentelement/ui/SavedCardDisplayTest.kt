package com.xmoney.paymentelement.ui

import com.xmoney.payments.model.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardDisplayTest {
    @Test
    fun savedCardsSummarySubtitle_joinsDistinctIssuers() {
        val cards = listOf(
            SavedCard("1", "•••• 1111", "visa", "12/26", issuerName = "ING", cardBrand = "visa"),
            SavedCard("2", "•••• 5599", "mastercard", "12/34", issuerName = "Revolut", cardBrand = "mastercard"),
            SavedCard("3", "•••• 7043", "visa", "08/29", issuerName = "BCR", cardBrand = "visa"),
        )
        assertEquals("ING, Revolut, BCR", savedCardsSummarySubtitle(cards))
    }

    @Test
    fun savedCardDisplayName_formatsIssuerAndMaskedNumber() {
        val card = SavedCard("1", "411111******1111", "visa", "12/26", issuerName = "ING", cardBrand = "visa")
        assertEquals("ING •••• 1111", savedCardDisplayName(card))
    }

    @Test
    fun savedCardMeta_includesBrandAndExpiry() {
        val card = SavedCard("1", "•••• 1111", "visa", "12/26", issuerName = "ING", cardBrand = "visa")
        assertEquals("Visa · 12/26", savedCardMeta(card, "en-US"))
    }

    @Test
    fun savedCardMeta_includesDefaultLabel() {
        val card = SavedCard(
            "1",
            "•••• 1111",
            "visa",
            "12/26",
            issuerName = "ING",
            cardBrand = "visa",
            isDefault = true,
        )
        assertEquals("Visa · 12/26 · Default", savedCardMeta(card, "en-US"))
    }

    @Test
    fun savedCardBrandForIcon_usesNetworkBrandNotIssuer() {
        val card = SavedCard("1", "•••• 1111", "visa", "12/26", issuerName = "ING", cardBrand = "visa")
        assertEquals("visa", savedCardBrandForIcon(card))
    }
}
