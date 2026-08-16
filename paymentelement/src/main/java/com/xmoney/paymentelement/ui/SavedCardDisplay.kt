package com.xmoney.paymentelement.ui

import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.config.Strings

private val KNOWN_CARD_BRANDS = setOf(
    "visa",
    "mastercard",
    "maestro",
    "amex",
    "american express",
    "discover",
    "diners",
    "jcb",
)

internal fun savedCardsSummarySubtitle(cards: List<SavedCard>): String =
    cards.map { savedCardIssuerLabel(it) }
        .filter { it != "Card" }
        .distinct()
        .joinToString(", ")

internal fun savedCardDisplayName(card: SavedCard): String {
    val issuer = savedCardIssuerLabel(card)
    val masked = savedCardMaskedNumber(card)
    return if (masked.isNotEmpty()) "$issuer $masked" else issuer
}

internal fun savedCardMeta(card: SavedCard, locale: String): String {
    val brand = savedCardBrandLabel(card)
    val expiry = card.cardExpiryDate?.trim().orEmpty()
    val base = if (expiry.isNotEmpty()) "$brand · $expiry" else brand
    return if (card.isDefault) {
        "$base · ${Strings.text("sheet.default", locale)}"
    } else {
        base
    }
}

internal fun savedCardBrandForIcon(card: SavedCard): String? =
    savedCardNetworkBrand(card)?.lowercase()

private fun savedCardIssuerLabel(card: SavedCard): String {
    card.issuerName?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    card.cardType?.takeIf { it.isNotBlank() && !isKnownCardBrand(it) }?.let { return it.trim() }
    val masked = card.cardNumber?.trim().orEmpty()
    if (masked.isNotEmpty()) {
        val prefix = masked.takeWhile { it != '•' && it != '*' && !it.isDigit() }.trim()
        if (prefix.isNotEmpty()) return prefix
    }
    return "Card"
}

private fun savedCardBrandLabel(card: SavedCard): String {
    val brand = savedCardNetworkBrand(card) ?: return "Card"
    return when (brand.lowercase()) {
        "visa" -> "Visa"
        "mastercard" -> "Mastercard"
        "maestro" -> "Maestro"
        "amex", "american express" -> "Amex"
        else -> brand.replaceFirstChar { it.uppercase() }
    }
}

private fun savedCardNetworkBrand(card: SavedCard): String? {
    card.cardBrand?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    card.cardType?.takeIf { it.isNotBlank() && isKnownCardBrand(it) }?.let { return it.trim() }
    return null
}

internal fun savedCardMaskedNumber(card: SavedCard): String {
    val raw = card.cardNumber?.trim().orEmpty()
    if (raw.isEmpty()) return "••••"

    val maskedMatch = Regex("""[•*]{4}\s*(\d{4})""").find(raw)
    if (maskedMatch != null) return "•••• ${maskedMatch.groupValues[1]}"

    val digits = raw.filter { it.isDigit() }
    if (digits.length >= 4) return "•••• ${digits.takeLast(4)}"

    return raw
}

private fun isKnownCardBrand(value: String): Boolean =
    value.trim().lowercase() in KNOWN_CARD_BRANDS
