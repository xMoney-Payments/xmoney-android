package com.xmoney.example.playground

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xmoney.example.BuildConfig
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleLoader
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.ExampleTopBar
import com.xmoney.example.ui.MerchantReadyGate
import com.xmoney.paymentelement.EmbeddedEvent
import com.xmoney.paymentelement.EmbeddedPaymentController
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.config.OptionsConfig
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.SavedCardsConfig
import com.xmoney.payments.config.UserInterfaceStyle
import com.xmoney.payments.model.PaymentIntent

@Composable
fun AppearancePlaygroundScreen(
    initialState: DemoAppearanceEditorState,
    onBack: () -> Unit,
    onApply: (DemoAppearanceEditorState) -> Unit,
    applyLabel: String = "Apply to checkout",
) {
    val draft = remember { initialState.snapshot() }
    var pendingOrder by remember { mutableStateOf<PaymentIntent?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var surfacesExpanded by remember { mutableStateOf(true) }
    var accentExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    var bordersExpanded by remember { mutableStateOf(false) }
    var payExpanded by remember { mutableStateOf(true) }
    var typeExpanded by remember { mutableStateOf(false) }
    var shapesExpanded by remember { mutableStateOf(false) }
    val previewStyle = playgroundResolvedStyle(UserInterfaceStyle.from(draft.style))

    val configuration = remember(previewStyle) {
        PaymentConfig(
            publicKey = BuildConfig.PUBLIC_KEY,
            paymentMethods = PaymentMethodsConfig(
                googlePay = GooglePayConfig(enabled = false),
            ),
            card = CardConfig(
                savedCards = SavedCardsConfig(enabled = false),
            ),
            options = OptionsConfig(
                locale = "en-US",
                style = previewStyle,
                appearance = draft.toAppearanceConfig(),
            ),
        )
    }

    val embedded = key(previewStyle) {
        rememberEmbeddedPayment(
            configuration = configuration,
            onResult = {},
        )
    }
    LaunchedEffect(draft.appearanceSignature) {
        embedded.updateAppearance(draft.toAppearanceConfig())
    }
    LaunchedEffect(previewStyle) { embedded.updateStyle(previewStyle) }

    LaunchedEffect(Unit) {
        DemoCheckoutBackend.secretsError()?.let {
            error = it
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        try {
            pendingOrder = DemoCheckoutBackend.createPaymentIntent(
                description = BuildConfig.DESCRIPTION,
            )
        } catch (e: Exception) {
            pendingOrder = null
            error = e.message ?: "Failed to create preview order"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = { draft.applyPreset(DemoAppearancePresets.default) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset to Default")
                }
                ExampleButton(
                    label = applyLabel,
                    onClick = { onApply(draft) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExampleTopBar(
                title = "Appearance",
                subtitle = "AppearanceConfig tokens with a live preview.",
                onBack = onBack,
                showThemeToggle = false,
            )

            PreviewSection(
                isLoading = isLoading,
                error = error,
                pendingOrder = pendingOrder,
                embedded = embedded,
            )

            ClassicsGallery(
                selectedPresetId = draft.selectedPresetId,
                onPresetSelected = { draft.applyPreset(it) },
            )

            PlaygroundSection(
                title = "Style",
                caption = "Which palette the form uses. Auto follows the example sun/moon toggle.",
            ) {
                PlaygroundSegmentedRow(
                    options = playgroundStyleOptions,
                    selected = playgroundStyleOptions.option(draft.style),
                    onSelected = { draft.updateStyle(it.value) },
                )
                PlaygroundLabeledBlock(
                    label = "Palette",
                    caption = "Light and Dark override Shared in that mode; empty fields fall back to Shared, then SDK defaults.",
                ) {
                    PlaygroundSegmentedRow(
                        options = playgroundPaletteOptions,
                        selected = playgroundPaletteOptions.option(draft.paletteSlot.name),
                        onSelected = {
                            draft.paletteSlot = PaletteSlot.valueOf(it.value)
                        },
                    )
                }
            }

            val palette = draft.currentPalette
            val allowEmpty = draft.paletteSlot != PaletteSlot.Shared
            val fallback = draft.shared

            PlaygroundExpandableGroup(
                title = "Surfaces",
                caption = "Form/sheet vs field surfaces vs method outline",
                expanded = surfacesExpanded,
                onExpandedChange = { surfacesExpanded = it },
            ) {
                TokenColor(
                    title = "Background",
                    apiName = "background",
                    hint = "Form / sheet background",
                    value = palette.background,
                    fallback = fallback.background,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { background = it } },
                )
                TokenColor(
                    title = "Component background",
                    apiName = "componentBackground",
                    hint = "Field and card surfaces",
                    value = palette.componentBackground,
                    fallback = fallback.componentBackground,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { componentBackground = it } },
                )
                TokenColor(
                    title = "Container border",
                    apiName = "containerBorder",
                    hint = "Method / saved-card outline",
                    value = palette.containerBorder,
                    fallback = fallback.containerBorder,
                    allowEmpty = allowEmpty,
                    allowNone = true,
                    onValueChange = { draft.editCurrentPalette { containerBorder = it } },
                )
            }

            PlaygroundExpandableGroup(
                title = "Accent",
                caption = "Selection, links, focused fields, checkbox; error chrome",
                expanded = accentExpanded,
                onExpandedChange = { accentExpanded = it },
            ) {
                TokenColor(
                    title = "Primary",
                    apiName = "primary",
                    hint = "Selection, links, cursor, focused field outline, checkbox; default Pay fill",
                    value = palette.primary,
                    fallback = fallback.primary,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { primary = it } },
                )
                TokenColor(
                    title = "Error",
                    apiName = "error",
                    hint = "Field error outline and text; sheet error text",
                    value = palette.error,
                    fallback = fallback.error,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { error = it } },
                )
            }

            PlaygroundExpandableGroup(
                title = "Text",
                caption = "Titles, muted copy, typed text, placeholders, icons",
                expanded = textExpanded,
                onExpandedChange = { textExpanded = it },
            ) {
                TokenColor(
                    title = "Primary text",
                    apiName = "primaryText",
                    hint = "Titles and body",
                    value = palette.primaryText,
                    fallback = fallback.primaryText,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { primaryText = it } },
                )
                TokenColor(
                    title = "Secondary text",
                    apiName = "secondaryText",
                    hint = "Muted copy",
                    value = palette.secondaryText,
                    fallback = fallback.secondaryText,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { secondaryText = it } },
                )
                TokenColor(
                    title = "Component text",
                    apiName = "componentText",
                    hint = "Typed field text",
                    value = palette.componentText,
                    fallback = fallback.componentText,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { componentText = it } },
                )
                TokenColor(
                    title = "Placeholder",
                    apiName = "placeholderText",
                    hint = "Field placeholders",
                    value = palette.placeholderText,
                    fallback = fallback.placeholderText,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { placeholderText = it } },
                )
                TokenColor(
                    title = "Icon",
                    apiName = "icon",
                    hint = "Icons (dark-mode muted icons)",
                    value = palette.icon,
                    fallback = fallback.icon,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { icon = it } },
                )
            }

            PlaygroundExpandableGroup(
                title = "Borders",
                caption = "Field outlines and condensed inner hairlines",
                expanded = bordersExpanded,
                onExpandedChange = { bordersExpanded = it },
            ) {
                TokenColor(
                    title = "Component border",
                    apiName = "componentBorder",
                    hint = "Spaced field outlines and the condensed card box",
                    value = palette.componentBorder,
                    fallback = fallback.componentBorder,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { componentBorder = it } },
                )
                TokenColor(
                    title = "Component divider",
                    apiName = "componentDivider",
                    hint = "Condensed inner hairlines",
                    value = palette.componentDivider,
                    fallback = fallback.componentDivider,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { componentDivider = it } },
                )
            }

            PlaygroundExpandableGroup(
                title = "Pay button",
                caption = "Pay fill and label; border width is resolved but not stroked today",
                expanded = payExpanded,
                onExpandedChange = { payExpanded = it },
            ) {
                TokenColor(
                    title = "Background",
                    apiName = "primaryButton.colors.background",
                    hint = "Pay button fill",
                    value = palette.primaryButtonBackground,
                    fallback = fallback.primaryButtonBackground,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { primaryButtonBackground = it } },
                )
                TokenColor(
                    title = "Text",
                    apiName = "primaryButton.colors.text",
                    hint = "Pay label and loader",
                    value = palette.primaryButtonText,
                    fallback = fallback.primaryButtonText,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { primaryButtonText = it } },
                )
                TokenColor(
                    title = "Border",
                    apiName = "primaryButton.colors.border",
                    hint = "Resolved; Pay button does not stroke a border today",
                    value = palette.primaryButtonBorder,
                    fallback = fallback.primaryButtonBorder,
                    allowEmpty = allowEmpty,
                    onValueChange = { draft.editCurrentPalette { primaryButtonBorder = it } },
                )
                PlaygroundSliderRow(
                    label = "Button radius",
                    value = draft.primaryButtonBorderRadius.coerceAtMost(32f),
                    valueRange = 0f..32f,
                    valueLabel = if (draft.primaryButtonBorderRadius >= 26f) {
                        "pill"
                    } else {
                        "${draft.primaryButtonBorderRadius.toInt()} dp"
                    },
                    caption = "appearance.primaryButton.borderRadius — default is a pill (9999 dp).",
                    onValueChange = {
                        draft.primaryButtonBorderRadius = it
                        draft.markCustomized()
                    },
                )
                PlaygroundSliderRow(
                    label = "Button border width",
                    value = draft.primaryButtonBorderWidth,
                    valueRange = 0f..4f,
                    valueLabel = String.format("%.1f dp", draft.primaryButtonBorderWidth),
                    caption = "Resolved into theme; not drawn on the Pay button currently.",
                    onValueChange = {
                        draft.primaryButtonBorderWidth = it
                        draft.markCustomized()
                    },
                )
                PlaygroundDropdown(
                    label = "Button font",
                    caption = "appearance.primaryButton.font.family — falls back to the form font.",
                    options = playgroundFontFamilyOptions,
                    selected = playgroundFontFamilyOptions.option(draft.primaryButtonFontFamily),
                    onSelected = {
                        draft.primaryButtonFontFamily = it.value
                        draft.markCustomized()
                    },
                )
            }

            PlaygroundExpandableGroup(
                title = "Type",
                caption = "Empty family = bundled Roobert",
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
            ) {
                PlaygroundDropdown(
                    label = "Font family",
                    caption = "Android typeface name. Empty uses bundled Roobert.",
                    options = playgroundFontFamilyOptions,
                    selected = playgroundFontFamilyOptions.option(draft.fontFamily),
                    onSelected = {
                        draft.fontFamily = it.value
                        draft.markCustomized()
                    },
                )
                PlaygroundSliderRow(
                    label = "Font scale",
                    value = draft.fontScale,
                    valueRange = 0.85f..1.25f,
                    valueLabel = String.format("%.2f×", draft.fontScale),
                    onValueChange = {
                        draft.fontScale = it
                        draft.markCustomized()
                    },
                )
            }

            PlaygroundExpandableGroup(
                title = "Shapes",
                caption = "Spaced fields, condensed card box, and payment-methods container",
                expanded = shapesExpanded,
                onExpandedChange = { shapesExpanded = it },
            ) {
                PlaygroundSliderRow(
                    label = "Border radius",
                    value = draft.borderRadius,
                    valueRange = 0f..28f,
                    valueLabel = "${draft.borderRadius.toInt()} dp",
                    caption = "appearance.borderRadius — default 16 dp on fields, 20 dp on the methods container.",
                    onValueChange = {
                        draft.borderRadius = it
                        draft.markCustomized()
                    },
                )
                PlaygroundSliderRow(
                    label = "Border width",
                    value = draft.borderWidth,
                    valueRange = 0f..3f,
                    valueLabel = String.format("%.1f dp", draft.borderWidth),
                    caption = "appearance.borderWidth — field outlines. Invalid fields use at least 1.5 dp.",
                    onValueChange = {
                        draft.borderWidth = it
                        draft.markCustomized()
                    },
                )
            }
        }
    }
}

@Composable
private fun TokenColor(
    title: String,
    apiName: String,
    hint: String,
    value: String,
    fallback: String,
    allowEmpty: Boolean,
    onValueChange: (String) -> Unit,
    allowNone: Boolean = false,
) {
    PlaygroundColorRow(
        title = title,
        apiName = apiName,
        hint = hint,
        value = value,
        onValueChange = onValueChange,
        allowNone = allowNone,
        allowEmpty = allowEmpty,
        fallbackSwatch = parseDemoHexColor(fallback),
    )
}

@Composable
private fun PreviewSection(
    isLoading: Boolean,
    error: String?,
    pendingOrder: PaymentIntent?,
    embedded: EmbeddedPaymentController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Live preview", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Embedded element updates as you change tokens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            error != null -> ExampleStatusChip(error, ExampleStatusKind.Error)
            isLoading && pendingOrder == null -> ExampleLoader(message = "Preparing preview…")
            pendingOrder != null -> {
                var ready by remember(embedded) { mutableStateOf(false) }
                val shape = RoundedCornerShape(ExampleRadii.card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                ) {
                    MerchantReadyGate(ready = ready, message = "Preparing preview…") {
                        PaymentElement(
                            controller = embedded,
                            intent = pendingOrder,
                            modifier = Modifier.fillMaxWidth(),
                            onEvent = { event ->
                                if (event is EmbeddedEvent.Ready) ready = true
                            },
                        )
                    }
                }
            }
        }
    }
}
