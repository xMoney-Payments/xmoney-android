package com.xmoney.payments.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppearanceColors(
    val primary: String? = null,
    val background: String? = null,
    val componentBackground: String? = null,
    val componentBorder: String? = null,
    val componentDivider: String? = null,
    val primaryText: String? = null,
    val secondaryText: String? = null,
    val componentText: String? = null,
    val placeholderText: String? = null,
    val icon: String? = null,
    val error: String? = null,
    val containerBorder: String? = null,
) : Parcelable {
    companion object {
        fun from(dict: Map<String, Any?>?): AppearanceColors? {
            if (dict == null) return null
            return AppearanceColors(
                primary = dict["primary"] as? String,
                background = dict["background"] as? String,
                componentBackground = dict["componentBackground"] as? String,
                componentBorder = dict["componentBorder"] as? String,
                componentDivider = dict["componentDivider"] as? String,
                primaryText = dict["primaryText"] as? String,
                secondaryText = dict["secondaryText"] as? String,
                componentText = dict["componentText"] as? String,
                placeholderText = dict["placeholderText"] as? String,
                icon = dict["icon"] as? String,
                error = dict["error"] as? String,
                containerBorder = dict["containerBorder"] as? String,
            )
        }
    }
}

@Parcelize
data class PrimaryButtonColors(
    val background: String? = null,
    val text: String? = null,
    val border: String? = null,
) : Parcelable {
    companion object {
        fun from(dict: Map<String, Any?>?): PrimaryButtonColors? {
            if (dict == null) return null
            return PrimaryButtonColors(
                background = dict["background"] as? String,
                text = dict["text"] as? String,
                border = dict["border"] as? String,
            )
        }
    }
}

@Parcelize
data class PrimaryButtonConfig(
    val fontFamily: String? = null,
    val colors: PrimaryButtonColors? = null,
    val colorsLight: PrimaryButtonColors? = null,
    val colorsDark: PrimaryButtonColors? = null,
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
) : Parcelable {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(dict: Map<String, Any?>?): PrimaryButtonConfig? {
            if (dict == null) return null
            val shapes = dict["shapes"] as? Map<String, Any?>
            val font = dict["font"] as? Map<String, Any?>
            return PrimaryButtonConfig(
                fontFamily = font?.get("family") as? String,
                colors = PrimaryButtonColors.from(dict["colors"] as? Map<String, Any?>),
                colorsLight = PrimaryButtonColors.from(dict["colorsLight"] as? Map<String, Any?>),
                colorsDark = PrimaryButtonColors.from(dict["colorsDark"] as? Map<String, Any?>),
                borderRadius = (shapes?.get("borderRadius") as? Number)?.toFloat(),
                borderWidth = (shapes?.get("borderWidth") as? Number)?.toFloat(),
            )
        }
    }
}

@Parcelize
data class AppearanceConfig(
    val fontFamily: String? = null,
    val fontScale: Float? = null,
    val colors: AppearanceColors? = null,
    val colorsLight: AppearanceColors? = null,
    val colorsDark: AppearanceColors? = null,
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
    val primaryButton: PrimaryButtonConfig? = null,
) : Parcelable {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(dict: Map<String, Any?>?): AppearanceConfig {
            if (dict == null) return AppearanceConfig()
            val font = dict["font"] as? Map<String, Any?>
            val shapes = dict["shapes"] as? Map<String, Any?>
            return AppearanceConfig(
                fontFamily = font?.get("family") as? String,
                fontScale = (font?.get("scale") as? Number)?.toFloat(),
                colors = AppearanceColors.from(dict["colors"] as? Map<String, Any?>),
                colorsLight = AppearanceColors.from(dict["colorsLight"] as? Map<String, Any?>),
                colorsDark = AppearanceColors.from(dict["colorsDark"] as? Map<String, Any?>),
                borderRadius = (shapes?.get("borderRadius") as? Number)?.toFloat(),
                borderWidth = (shapes?.get("borderWidth") as? Number)?.toFloat(),
                primaryButton = PrimaryButtonConfig.from(dict["primaryButton"] as? Map<String, Any?>),
            )
        }
    }
}

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
