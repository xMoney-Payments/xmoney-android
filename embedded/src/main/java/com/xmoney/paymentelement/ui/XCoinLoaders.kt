package com.xmoney.paymentelement.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmoney.payments.R as CoreR
import com.xmoney.paymentelement.theme.CheckoutTheme

private val FlipEasing = CubicBezierEasing(0.55f, 0.02f, 0.35f, 1f)
private val ButtonCoinEasing = CubicBezierEasing(0.5f, 0.05f, 0.35f, 1f)

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun XCoinFlipLoader(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "xCoinFlip")
    val rotationY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                0f at 0 using FlipEasing
                180f at 988 using LinearEasing
                180f at 1300 using FlipEasing
                360f at 2288 using LinearEasing
                360f at 2600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "xCoinFlipRotation",
    )

    val markHeight = size * (418f / 539f)
    val cameraDistance = with(density) { 12.dp.toPx() }

    Image(
        painter = painterResource(CoreR.drawable.xmoney_ic_xmark),
        contentDescription = "Loading",
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .width(size)
            .height(markHeight)
            .graphicsLayer {
                this.rotationY = rotationY
                this.cameraDistance = cameraDistance
            },
    )
}

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun XCoinButtonMark(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 17.dp,
) {
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "xBtnCoin")
    val rotationY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                0f at 0 using ButtonCoinEasing
                360f at 825 using LinearEasing
                360f at 1500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "xBtnCoinRotation",
    )
    val markHeight = size * (418f / 539f)
    val cameraDistance = with(density) { 8.dp.toPx() }

    Image(
        painter = painterResource(CoreR.drawable.xmoney_ic_xmark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .width(size)
            .height(markHeight)
            .graphicsLayer {
                this.rotationY = rotationY
                this.cameraDistance = cameraDistance
            },
    )
}

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun XCoinButtonLoadingContent(
    theme: CheckoutTheme,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XCoinButtonMark(color = theme.primaryButtonText)
        Text(
            text = label,
            color = theme.primaryButtonText,
            fontSize = theme.scaledSp(16f),
            fontWeight = FontWeight.Bold,
            fontFamily = theme.fontFamily,
        )
    }
}
