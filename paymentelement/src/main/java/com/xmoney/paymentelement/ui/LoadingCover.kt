package com.xmoney.paymentelement.ui

import androidx.annotation.RestrictTo
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings

/**
 * SDK loading coin. Swallows touches without becoming focusable, and is
 * announced politely as "Loading" in [locale].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun LoadingCover(
    locale: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit = { XCoinFlipLoader(color = CheckoutTheme.BrandPrimary) },
) {
    val label = Strings.text("sheet.loading", locale)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .clearAndSetSemantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            }
            .padding(40.dp),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Keeps a covered subtree out of accessibility and out of keyboard or D-pad focus. */
@OptIn(ExperimentalComposeUiApi::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun Modifier.hiddenFromUser(hidden: Boolean): Modifier =
    if (!hidden) {
        this
    } else {
        this
            .clearAndSetSemantics { }
            .focusProperties { enter = { FocusRequester.Cancel } }
            .focusGroup()
    }
