package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme

internal enum class CardBrandIconSize {
  FieldTrailing,
  SavedCardRow,
  UseOtherCardPair,
}

@Composable
internal fun CardBrandIcon(
    brand: String?,
    theme: CheckoutTheme,
    size: CardBrandIconSize = CardBrandIconSize.FieldTrailing,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val spec = brandIconSpec(brand, size)
    val painter = painterResource(spec.drawableRes)
    val resolvedTint = tint ?: visaMarkTint(brand, theme)
    Image(
        painter = painter,
        contentDescription = spec.contentDescription,
        modifier = modifier
            .height(spec.height)
            .then(if (spec.width != null) Modifier.width(spec.width) else Modifier),
        contentScale = ContentScale.Fit,
        colorFilter = resolvedTint?.let { ColorFilter.tint(it) },
    )
}

@Composable
internal fun VisaMastercardMarks(
    theme: CheckoutTheme,
    size: CardBrandIconSize = CardBrandIconSize.UseOtherCardPair,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CardBrandIcon(brand = "visa", theme = theme, size = size)
        CardBrandIcon(brand = "mastercard", theme = theme, size = size)
    }
}

private fun visaMarkTint(brand: String?, theme: CheckoutTheme): Color? {
    if (!brand.equals("visa", ignoreCase = true)) return null
    return theme.visaMarkTint
}

private data class BrandIconSpec(
    val drawableRes: Int,
    val height: Dp,
    val width: Dp? = null,
    val contentDescription: String,
)

private fun brandIconSpec(brand: String?, size: CardBrandIconSize): BrandIconSpec {
    return when (brand?.lowercase()) {
        "visa" -> BrandIconSpec(
            drawableRes = R.drawable.xmoney_ic_visa,
            height = when (size) {
                CardBrandIconSize.FieldTrailing -> 11.dp
                CardBrandIconSize.SavedCardRow -> 10.dp
                CardBrandIconSize.UseOtherCardPair -> 9.dp
            },
            width = when (size) {
                CardBrandIconSize.FieldTrailing -> 34.dp
                CardBrandIconSize.SavedCardRow -> 31.dp
                CardBrandIconSize.UseOtherCardPair -> 28.dp
            },
            contentDescription = "Visa",
        )
        "mastercard" -> BrandIconSpec(
            drawableRes = R.drawable.xmoney_ic_mastercard,
            height = when (size) {
                CardBrandIconSize.FieldTrailing -> 19.dp
                CardBrandIconSize.SavedCardRow -> 18.dp
                CardBrandIconSize.UseOtherCardPair -> 15.dp
            },
            width = when (size) {
                CardBrandIconSize.FieldTrailing -> 30.dp
                CardBrandIconSize.SavedCardRow -> 29.dp
                CardBrandIconSize.UseOtherCardPair -> 24.dp
            },
            contentDescription = "Mastercard",
        )
        else -> BrandIconSpec(
            drawableRes = R.drawable.xmoney_ic_card_generic,
            height = when (size) {
                CardBrandIconSize.FieldTrailing -> 24.dp
                CardBrandIconSize.SavedCardRow -> 20.dp
                CardBrandIconSize.UseOtherCardPair -> 18.dp
            },
            width = when (size) {
                CardBrandIconSize.FieldTrailing -> 24.dp
                CardBrandIconSize.SavedCardRow -> 20.dp
                CardBrandIconSize.UseOtherCardPair -> 18.dp
            },
            contentDescription = "Card",
        )
    }
}
