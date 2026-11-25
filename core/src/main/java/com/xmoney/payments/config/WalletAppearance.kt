package com.xmoney.payments.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class WalletButtonColor(val value: String) {
    WHITE("white"),
    BLACK("black"),
    ;

    companion object {
        fun from(raw: String?): WalletButtonColor? {
            if (raw == null) return null
            return when (raw.trim().lowercase()) {
                "white", "light" -> WHITE
                "black", "dark" -> BLACK
                else -> entries.firstOrNull { it.value.equals(raw, ignoreCase = true) }
            }
        }
    }
}

enum class WalletButtonType(val value: String) {
    PLAIN("plain"),
    PAY("pay"),
    BUY("buy"),
    BOOK("book"),
    CHECKOUT("checkout"),
    DONATE("donate"),
    ORDER("order"),
    SUBSCRIBE("subscribe"),
    ;

    companion object {
        fun from(raw: String?): WalletButtonType? =
            raw?.let { entries.firstOrNull { e -> e.value.equals(it, ignoreCase = true) } }
    }
}

@Parcelize
data class WalletAppearance(
    val color: WalletButtonColor? = null,
    val radius: Float? = null,
    val type: WalletButtonType? = null,
) : Parcelable
