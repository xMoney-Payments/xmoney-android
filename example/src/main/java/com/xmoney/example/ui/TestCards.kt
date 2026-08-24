package com.xmoney.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmoney.example.theme.ExampleColors
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.theme.LocalExampleSemantics
import kotlinx.coroutines.delay

internal const val TEST_CARD_SUCCESS_PAN = "4111111111111111"

private enum class DemoCardBrand(val label: String) {
    Visa("Visa"),
    Mastercard("Mastercard"),
}

private data class DemoTestCard(
    val brand: DemoCardBrand,
    val pan: String,
    val digits: String,
    val expiry: String,
    val cvv: String,
    val threeDS: String,
    val success: Boolean,
    val status: String,
) {
    val copyAll: String get() = "$pan  $expiry  $cvv  $threeDS"
}

private val DemoTestCards = listOf(
    DemoTestCard(
        brand = DemoCardBrand.Mastercard,
        pan = "5555 5555 5555 5599",
        digits = "5555555555555599",
        expiry = "12/34",
        cvv = "123",
        threeDS = "00000",
        success = true,
        status = "Success (3DS2)",
    ),
    DemoTestCard(
        brand = DemoCardBrand.Visa,
        pan = "4111 1111 1111 1111",
        digits = TEST_CARD_SUCCESS_PAN,
        expiry = "12/26",
        cvv = "123",
        threeDS = "00000",
        success = true,
        status = "Success (3DS2 Frictionless)",
    ),
    DemoTestCard(
        brand = DemoCardBrand.Mastercard,
        pan = "5168 4948 9505 5780",
        digits = "5168494895055780",
        expiry = "12/26",
        cvv = "123",
        threeDS = "00000",
        success = false,
        status = "Fail (3DS2 Frictionless)",
    ),
    DemoTestCard(
        brand = DemoCardBrand.Visa,
        pan = "4000 0011 1111 1118",
        digits = "4000001111111118",
        expiry = "12/30",
        cvv = "123",
        threeDS = "00000",
        success = true,
        status = "Success (3DS2 Attempt)",
    ),
)

@Composable
fun TestCardsAction(
    modifier: Modifier = Modifier,
    nameCheckHint: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Text("Test cards", style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        TestCardsSheet(
            nameCheckHint = nameCheckHint,
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestCardsSheet(
    nameCheckHint: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copied) {
        val label = copied ?: return@LaunchedEffect
        delay(1_400)
        if (copied == label) copied = null
    }

    fun copy(value: String, label: String) {
        clipboard.setText(AnnotatedString(value))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        copied = label
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Test cards", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "xMoney test cards. Tap a number to copy it into the form.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DemoTestCards.forEach { card ->
                    TestCardRow(card = card, onCopy = ::copy)
                }
                if (nameCheckHint) {
                    ExampleCard {
                        Text(
                            "NAME CHECK",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "This sample expects John Doe. Use a test card whose account-validation result matches that name, or pay is blocked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            CopiedBanner(
                label = copied,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CopiedBanner(
    label: String?,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableStateOf(label) }
    if (label != null) displayed = label
    AnimatedVisibility(
        visible = label != null,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = "Copied $displayed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(ExampleRadii.pill))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TestCardRow(
    card: DemoTestCard,
    onCopy: (String, String) -> Unit,
) {
    ExampleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BrandTile(card.brand)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { onCopy(card.pan, "PAN") },
                        ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = card.pan,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelLarge,
                        color = ExampleColors.Purple,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TestCardStatusPill(success = card.success, label = card.status)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TestCardCopyField(
                title = "Expiry",
                value = card.expiry,
                modifier = Modifier.weight(1f),
                onCopy = { onCopy(card.expiry, "expiry") },
            )
            TestCardCopyField(
                title = "CVV",
                value = card.cvv,
                modifier = Modifier.weight(1f),
                onCopy = { onCopy(card.cvv, "CVV") },
            )
            TestCardCopyField(
                title = "3DS",
                value = card.threeDS,
                modifier = Modifier.weight(1f),
                onCopy = { onCopy(card.threeDS, "3DS") },
            )
        }
        Text(
            text = "Copy all",
            style = MaterialTheme.typography.labelLarge,
            color = ExampleColors.Purple,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { onCopy(card.copyAll, "card") },
            ),
        )
    }
}

@Composable
private fun TestCardCopyField(
    title: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onCopy,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun TestCardStatusPill(success: Boolean, label: String) {
    val semantics = LocalExampleSemantics.current
    val container = if (success) semantics.successSoft else semantics.dangerSoft
    val content = if (success) semantics.success else ExampleColors.Error
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ExampleRadii.pill))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(content),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun BrandTile(brand: DemoCardBrand) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(32.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape)
            .semantics { contentDescription = brand.label },
        contentAlignment = Alignment.Center,
    ) {
        when (brand) {
            DemoCardBrand.Visa -> Text(
                text = "VISA",
                color = Color(0xFF1434CB),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.sp,
            )
            DemoCardBrand.Mastercard -> Row(
                modifier = Modifier
                    .width(28.dp)
                    .height(16.dp),
                horizontalArrangement = Arrangement.spacedBy((-7).dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEB001B)),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF79E1B)),
                )
            }
        }
    }
}
