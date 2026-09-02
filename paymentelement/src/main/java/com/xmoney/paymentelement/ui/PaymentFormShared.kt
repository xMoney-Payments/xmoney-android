package com.xmoney.paymentelement.ui

import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.payments.validation.CardFieldValidators.localizedMessage
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.engine.SheetState
import com.xmoney.payments.config.Strings
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.ValidationMode
import androidx.compose.runtime.LaunchedEffect

internal enum class PaymentMethod { SavedCard, NewCard }

internal val PaymentRowContentPadding = 12.dp
internal val PaymentContainerInset = 6.dp
internal val NewCardFormHorizontalPadding = 10.dp
internal val SavedCardRowHeight = 64.dp
internal val SavedCardConfirmPanelHeight = 91.dp
internal const val MaxVisibleSavedCards = 3
internal val SheetContentHorizontalPadding = 22.dp
internal val SheetContentTopPadding = 16.dp
internal val SheetContentBottomPadding = 16.dp
internal val SheetOrDividerVerticalMargin = 18.dp
internal val SheetFooterTopPadding = 12.dp
internal val SheetFooterBottomPadding = 26.dp
internal val SheetFooterContentSpacing = 10.dp
internal const val SecondaryMetaAlpha = 0.45f
internal const val PressScale = 0.98f
internal const val SheetInDurationMs = 300

internal fun Modifier.topHairline(color: Color, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawLine(
        color = color,
        start = Offset(0f, stroke / 2f),
        end = Offset(size.width, stroke / 2f),
        strokeWidth = stroke,
    )
}

@Composable
internal fun SheetText(
    text: String,
    theme: CheckoutTheme,
    modifier: Modifier = Modifier,
    color: Color = theme.primaryText,
    fontSize: Float = 16f,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = theme.fontFamily,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: Float? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = theme.scaledSp(fontSize),
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight?.let { theme.scaledSp(it) } ?: TextUnit.Unspecified,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
internal fun Modifier.pressScaleClickable(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PressScale else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

@Composable
internal fun SubmitButton(
    theme: CheckoutTheme,
    title: String,
    locale: String,
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val background = theme.primaryButtonBackground
    val shape = RoundedCornerShape(theme.primaryButtonBorderRadius)
    val showShadow = enabled && !isProcessing
    Button(
        onClick = onClick,
        enabled = enabled && !isProcessing,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (!enabled && !isProcessing) 0.5f else 1f)
            .then(
                if (showShadow) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = theme.primary.copy(alpha = 0.30f),
                        spotColor = theme.primary.copy(alpha = 0.30f),
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = background,
            disabledBackgroundColor = background,
        ),
    ) {
        if (isProcessing) {
            XCoinButtonLoadingContent(
                theme = theme,
                label = Strings.text("button.processing", locale),
            )
        } else {
            SheetText(
                title,
                theme = theme,
                color = theme.primaryButtonText,
                fontWeight = FontWeight.Bold,
                fontSize = 16f,
                fontFamily = theme.primaryButtonFontFamily,
            )
        }
    }
}

@Composable
internal fun PoweredByFooter(theme: CheckoutTheme, locale: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetText(
            Strings.text("sheet.poweredBy", locale),
            theme = theme,
            color = theme.secondaryText.copy(alpha = 0.3f),
            fontSize = 12f,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Image(
            painter = painterResource(R.drawable.xmoney_ic_wordmark),
            contentDescription = "xMoney",
            modifier = Modifier.height(13.dp),
            colorFilter = ColorFilter.tint(theme.secondaryText.copy(alpha = 0.42f)),
        )
    }
}
