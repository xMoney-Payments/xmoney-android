package com.xmoney.paymentelement.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xmoney.paymentelement.R

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
object PaymentFontFamily {
    val family: FontFamily = FontFamily(
        Font(R.font.roobert_regular, FontWeight.Normal),
        Font(R.font.roobert_medium, FontWeight.Medium),
        Font(R.font.roobert_semibold, FontWeight.SemiBold),
        Font(R.font.roobert_bold, FontWeight.Bold),
    )
}
