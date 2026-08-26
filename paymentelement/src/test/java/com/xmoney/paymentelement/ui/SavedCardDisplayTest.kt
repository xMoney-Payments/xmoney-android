package com.xmoney.paymentelement.ui

import com.xmoney.payments.config.Strings
import com.xmoney.payments.model.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardDisplayTest {
    @Test
    fun savedCardsSummarySubtitle_joinsDistinctIssuers() {
        val cards = listOf(
            SavedCard("1", "•••• 1111", "visa", "12/26", bankName = "ING", cardBrand = "visa"),
            SavedCard("2", "•••• 5599", "mastercard", "12/34", bankName = "Revolut", cardBrand = "mastercard"),
            SavedCard("3", "•••• 7043", "visa", "08/29", bankName = "BCR", cardBrand = "visa"),
        )
        assertEquals("ING, Revolut, BCR", savedCardsSummarySubtitle(cards))
    }

    @Test
    fun savedCardDisplayName_formatsIssuerAndMaskedNumber() {
        val card = SavedCard("1", "411111******1111", "visa", "12/26", bankName = "ING", cardBrand = "visa")
        assertEquals("ING •••• 1111", savedCardDisplayName(card))
    }

    @Test
    fun savedCardMeta_includesBrandAndExpiry() {
        val card = SavedCard("1", "•••• 1111", "visa", "12/26", bankName = "ING", cardBrand = "visa")
        assertEquals("Visa · 12/26", savedCardMeta(card, "en-US"))
    }

    @Test
    fun savedCardMeta_includesDefaultLabel() {
        val card = SavedCard(
            "1",
            "•••• 1111",
            "visa",
            "12/26",
            bankName = "ING",
            cardBrand = "visa",
            isDefault = true,
        )
        assertEquals("Visa · 12/26 · Default", savedCardMeta(card, "en-US"))
    }

    @Test
    fun savedCardBrandForIcon_usesNetworkBrandNotIssuer() {
        val card = SavedCard("1", "•••• 1111", "visa", "12/26", bankName = "ING", cardBrand = "visa")
        assertEquals("visa", savedCardBrandForIcon(card))
    }

    @Test
    fun removeCardCopyKeys_matchPrototype() {
        assertEquals("Edit", Strings.text("sheet.edit", "en-US"))
        assertEquals("Done", Strings.text("sheet.done", "en-US"))
        assertEquals("Remove", Strings.text("sheet.remove", "en-US"))
        assertEquals("Keep it", Strings.text("sheet.keepIt", "en-US"))
        assertEquals(
            "Are you sure you want to remove this card?",
            Strings.text("sheet.removeCardConfirm", "en-US"),
        )
    }
}
