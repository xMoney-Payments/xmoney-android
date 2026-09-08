package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings

internal enum class CardField { HOLDER, NUMBER, EXPIRY, CVV }

@Composable
internal fun FieldLabel(
    text: String,
    theme: CheckoutTheme,
) {
    SheetText(
        text,
        theme = theme,
        fontSize = 14f,
        fontWeight = FontWeight.Medium,
        color = theme.primaryText,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun FieldErrorText(
    message: String?,
    theme: CheckoutTheme,
) {
    if (message.isNullOrBlank()) return
    SheetText(
        message,
        theme = theme,
        color = theme.errorText,
        fontSize = 12.5f,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun LabeledFieldBox(
    theme: CheckoutTheme,
    borderColor: Color,
    borderWidth: Dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.formFieldRadius))
            .background(theme.componentBackground)
            .border(borderWidth, borderColor, RoundedCornerShape(theme.formFieldRadius)),
        content = { content() },
    )
}

@Composable
internal fun LabeledCardFields(
    theme: CheckoutTheme,
    locale: String,
    showHolderName: Boolean,
    holderFirst: Boolean,
    holderIsLast: Boolean,
    number: String,
    expiry: String,
    cvv: String,
    holder: String,
    brand: String?,
    fieldHeight: Dp,
    numberBorderColor: Color,
    numberBorderWidth: Dp,
    numberError: String?,
    expiryBorderColor: Color,
    expiryBorderWidth: Dp,
    expiryError: String?,
    cvvBorderColor: Color,
    cvvBorderWidth: Dp,
    cvvError: String?,
    holderBorderColor: Color,
    holderBorderWidth: Dp,
    holderError: String?,
    onNumberChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onCvvChange: (String) -> Unit,
    onHolderChange: (String) -> Unit,
    onFocus: (CardField) -> Unit,
    onBlur: (CardField) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHolderName && holderFirst) {
            Column {
                FieldLabel(Strings.text("elements.cardholderName", locale), theme)
                LabeledFieldBox(theme, holderBorderColor, holderBorderWidth) {
                    ContainedFieldRow(
                        theme = theme,
                        value = holder,
                        placeholder = Strings.text("placeholder.cardholderName", locale),
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        fieldHeight = fieldHeight,
                        onFocus = { onFocus(CardField.HOLDER) },
                        onBlur = { onBlur(CardField.HOLDER) },
                        onValueChange = onHolderChange,
                    )
                }
                FieldErrorText(holderError, theme)
            }
        }

        Column {
            FieldLabel(Strings.text("elements.cardNumber", locale), theme)
            LabeledFieldBox(theme, numberBorderColor, numberBorderWidth) {
                ContainedFieldRow(
                    theme = theme,
                    value = number,
                    placeholder = Strings.text("placeholder.cardNumber.spaced", locale),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    fieldHeight = fieldHeight,
                    visualTransformation = CardNumberVisualTransformation,
                    trailing = {
                        CardBrandIcon(
                            brand = brand,
                            theme = theme,
                            size = CardBrandIconSize.FieldTrailing,
                            tint = if (brand == null) theme.mutedIcon else null,
                        )
                    },
                    onFocus = { onFocus(CardField.NUMBER) },
                    onBlur = { onBlur(CardField.NUMBER) },
                    onValueChange = onNumberChange,
                )
            }
            FieldErrorText(numberError, theme)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(Strings.text("elements.expDate", locale), theme)
                LabeledFieldBox(theme, expiryBorderColor, expiryBorderWidth) {
                    ContainedFieldRow(
                        theme = theme,
                        value = expiry,
                        placeholder = Strings.text("placeholder.expDate", locale),
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        fieldHeight = fieldHeight,
                        visualTransformation = ExpiryVisualTransformation,
                        onFocus = { onFocus(CardField.EXPIRY) },
                        onBlur = { onBlur(CardField.EXPIRY) },
                        onValueChange = onExpiryChange,
                    )
                }
                FieldErrorText(expiryError, theme)
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(Strings.text("elements.cvv", locale), theme)
                LabeledFieldBox(theme, cvvBorderColor, cvvBorderWidth) {
                    ContainedFieldRow(
                        theme = theme,
                        value = cvv,
                        placeholder = Strings.text("placeholder.cvv.spaced", locale),
                        keyboardType = KeyboardType.Number,
                        imeAction = if (holderIsLast) ImeAction.Next else ImeAction.Done,
                        fieldHeight = fieldHeight,
                        trailing = {
                            Image(
                                painter = painterResource(R.drawable.xmoney_ic_cvv),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(theme.mutedIcon),
                            )
                        },
                        onFocus = { onFocus(CardField.CVV) },
                        onBlur = { onBlur(CardField.CVV) },
                        onValueChange = onCvvChange,
                    )
                }
                FieldErrorText(cvvError, theme)
            }
        }

        if (showHolderName && !holderFirst) {
            Column {
                FieldLabel(Strings.text("elements.cardholderName", locale), theme)
                LabeledFieldBox(theme, holderBorderColor, holderBorderWidth) {
                    ContainedFieldRow(
                        theme = theme,
                        value = holder,
                        placeholder = Strings.text("placeholder.cardholderName", locale),
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        fieldHeight = fieldHeight,
                        onFocus = { onFocus(CardField.HOLDER) },
                        onBlur = { onBlur(CardField.HOLDER) },
                        onValueChange = onHolderChange,
                    )
                }
                FieldErrorText(holderError, theme)
            }
        }
    }
}

@Composable
internal fun ErrorBadgeMessage(message: String, theme: CheckoutTheme) {
    Row(
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(theme.errorBorder),
            contentAlignment = Alignment.Center,
        ) {
            SheetText("!", theme = theme, color = Color.White, fontSize = 10f, fontWeight = FontWeight.Bold)
        }
        SheetText(
            message,
            theme = theme,
            color = theme.errorText,
            fontSize = 12.5f,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SaveCardCheckbox(
    theme: CheckoutTheme,
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(top = 6.dp)
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .then(
                    if (checked) {
                        Modifier.background(theme.primary)
                    } else {
                        Modifier.border(1.5.dp, theme.unselectedRing, RoundedCornerShape(7.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Image(
                    painter = painterResource(R.drawable.xmoney_ic_check),
                    contentDescription = null,
                    modifier = Modifier
                        .width(12.dp)
                        .height(9.dp),
                    colorFilter = ColorFilter.tint(theme.primaryButtonText),
                )
            }
        }
        SheetText(
            label,
            theme = theme,
            fontSize = 14f,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun ContainedFieldRow(
    theme: CheckoutTheme,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    fieldHeight: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onFocus: () -> Unit = {},
    onBlur: () -> Unit = {},
    onValueChange: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            hadFocus = true
            onFocus()
        } else if (hadFocus) {
            onBlur()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = theme.componentText,
                fontSize = theme.scaledSp(18f),
                fontWeight = FontWeight.Medium,
                fontFamily = theme.fontFamily,
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = { focusManager.clearFocus() },
            ),
            interactionSource = interactionSource,
            cursorBrush = SolidColor(theme.primary),
            modifier = Modifier
                .weight(1f)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        SheetText(
                            placeholder,
                            theme = theme,
                            color = theme.placeholderText,
                            fontSize = 18f,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    inner()
                }
            },
        )
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}
