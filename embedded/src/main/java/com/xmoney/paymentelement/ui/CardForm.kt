package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.config.ValidationMode
import com.xmoney.payments.model.CardInput
import com.xmoney.payments.validation.CardFieldValidators
import com.xmoney.payments.validation.CardFieldValidators.localizedMessage

private val errorDisplayOrder = listOf(
    CardField.NUMBER,
    CardField.EXPIRY,
    CardField.CVV,
    CardField.HOLDER,
)

@Composable
internal fun CardForm(
    config: ResolvedPaymentConfig,
    theme: CheckoutTheme,
    showHolderName: Boolean,
    showSaveOptIn: Boolean,
    holderFirst: Boolean = false,
    sectionLabel: String? = null,
    saveCardLabel: String? = null,
    showErrors: Boolean = false,
    contentTopPadding: Dp = 10.dp,
    onChange: (CardInput, Boolean) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf<String?>(null) }
    val blurred = remember { mutableStateMapOf<CardField, Boolean>() }
    val focused = remember { mutableStateMapOf<CardField, Boolean>() }
    val errors = remember { mutableStateMapOf<CardField, CardFieldValidators.FieldError?>() }
    val validationMode = config.card.validationMode

    fun currentInput(): CardInput {
        val (month, year) = CardFieldValidators.parseExpiry(expiry)
        return CardInput(
            number = CardFieldValidators.normalizeDigits(number),
            expiryMonth = month,
            expiryYear = year,
            cvv = cvv,
            holderName = if (showHolderName) holder else null,
            saveCard = showSaveOptIn && save,
        )
    }

    fun runValidation(): Boolean {
        val input = currentInput()
        errors[CardField.NUMBER] = CardFieldValidators.validateCardNumber(input.number)
        errors[CardField.EXPIRY] = CardFieldValidators.validateExpiry(input.expiryMonth, input.expiryYear)
        errors[CardField.CVV] = CardFieldValidators.validateCVV(input.cvv)
        if (showHolderName) {
            errors[CardField.HOLDER] = CardFieldValidators.validateHolderName(input.holderName)
        } else {
            errors.remove(CardField.HOLDER)
        }
        return errors.values.all { it == null }
    }

    fun emitChange(validateNow: Boolean) {
        val input = currentInput()
        val isValid = if (validateNow) runValidation() else errors.values.all { it == null }
        onChange(input, isValid)
    }

    fun onFieldValueChange() {
        when (validationMode) {
            ValidationMode.ON_CHANGE -> emitChange(validateNow = true)
            ValidationMode.ON_TOUCHED -> {
                emitChange(validateNow = blurred.isNotEmpty())
            }
            ValidationMode.ON_BLUR,
            ValidationMode.ON_SUBMIT,
            -> emitChange(validateNow = false)
        }
    }

    fun onFieldBlur(field: CardField) {
        blurred[field] = true
        when (validationMode) {
            ValidationMode.ON_SUBMIT -> emitChange(validateNow = false)
            else -> emitChange(validateNow = true)
        }
    }

    LaunchedEffect(showErrors) {
        if (showErrors) emitChange(validateNow = true)
    }

    fun shouldShowError(field: CardField): Boolean {
        if (errors[field] == null) return false
        return when (validationMode) {
            ValidationMode.ON_CHANGE -> true
            ValidationMode.ON_SUBMIT -> showErrors
            ValidationMode.ON_BLUR,
            ValidationMode.ON_TOUCHED,
            -> showErrors || blurred[field] == true
        }
    }

    val hasVisibleError = errorDisplayOrder.any { shouldShowError(it) && errors[it] != null }
    val borderColor = if (hasVisibleError) theme.errorBorder else theme.fieldBorder
    val borderWidth = if (hasVisibleError) 1.5.dp else 1.dp
    val firstVisibleError = errorDisplayOrder.firstOrNull { shouldShowError(it) && errors[it] != null }
    val holderIsLast = showHolderName && !holderFirst
    val spacedInputs = config.card.inputs.isSpaced
    val locale = config.options.locale

    fun fieldBorderColor(field: CardField): Color =
        if (shouldShowError(field)) theme.errorBorder else theme.fieldBorder

    fun fieldBorderWidth(field: CardField): Dp =
        if (shouldShowError(field)) 1.5.dp else 1.dp

    fun fieldErrorMessage(field: CardField): String? {
        if (!shouldShowError(field)) return null
        return errors[field]?.localizedMessage(locale)
    }

    Column(
        modifier = Modifier.padding(top = contentTopPadding, bottom = 4.dp),
    ) {
        if (!sectionLabel.isNullOrBlank()) {
            SheetText(
                sectionLabel,
                theme = theme,
                color = theme.primaryText.copy(alpha = SecondaryMetaAlpha),
                fontSize = 13f,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (spacedInputs) {
            LabeledCardFields(
                theme = theme,
                locale = locale,
                showHolderName = showHolderName,
                holderFirst = holderFirst,
                holderIsLast = holderIsLast,
                number = number,
                expiry = expiry,
                cvv = cvv,
                holder = holder,
                brand = brand,
                fieldHeight = theme.formFieldHeight,
                numberBorderColor = fieldBorderColor(CardField.NUMBER),
                numberBorderWidth = fieldBorderWidth(CardField.NUMBER),
                numberError = fieldErrorMessage(CardField.NUMBER),
                expiryBorderColor = fieldBorderColor(CardField.EXPIRY),
                expiryBorderWidth = fieldBorderWidth(CardField.EXPIRY),
                expiryError = fieldErrorMessage(CardField.EXPIRY),
                cvvBorderColor = fieldBorderColor(CardField.CVV),
                cvvBorderWidth = fieldBorderWidth(CardField.CVV),
                cvvError = fieldErrorMessage(CardField.CVV),
                holderBorderColor = fieldBorderColor(CardField.HOLDER),
                holderBorderWidth = fieldBorderWidth(CardField.HOLDER),
                holderError = fieldErrorMessage(CardField.HOLDER),
                onNumberChange = {
                    val formatted = CardFieldValidators.formatCardNumber(it)
                    number = formatted.raw
                    brand = formatted.brand
                    onFieldValueChange()
                },
                onExpiryChange = {
                    expiry = CardFieldValidators.normalizeExpiryDigits(it)
                    onFieldValueChange()
                },
                onCvvChange = {
                    cvv = CardFieldValidators.normalizeDigits(it).take(4)
                    onFieldValueChange()
                },
                onHolderChange = {
                    holder = it
                    onFieldValueChange()
                },
                onFocus = { focused[it] = true },
                onBlur = { focused[it] = false; onFieldBlur(it) },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.formFieldRadius))
                    .background(theme.componentBackground)
                    .border(borderWidth, borderColor, RoundedCornerShape(theme.formFieldRadius)),
            ) {
                if (showHolderName && holderFirst) {
                    ContainedFieldRow(
                        theme = theme,
                        value = holder,
                        placeholder = Strings.text("placeholder.cardholderName", locale),
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        fieldHeight = theme.formFieldHeight,
                        onFocus = { focused[CardField.HOLDER] = true },
                        onBlur = { focused[CardField.HOLDER] = false; onFieldBlur(CardField.HOLDER) },
                        onValueChange = { holder = it; onFieldValueChange() },
                    )
                    Divider(color = theme.fieldDivider, thickness = 1.dp)
                }

                ContainedFieldRow(
                    theme = theme,
                    value = number,
                    placeholder = Strings.text("placeholder.cardNumber", locale),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    fieldHeight = theme.formFieldHeight,
                    visualTransformation = CardNumberVisualTransformation,
                    trailing = {
                        CardBrandIcon(
                            brand = brand,
                            size = CardBrandIconSize.FieldTrailing,
                            tint = if (brand == null) theme.mutedIcon else null,
                        )
                    },
                    onFocus = { focused[CardField.NUMBER] = true },
                    onBlur = { focused[CardField.NUMBER] = false; onFieldBlur(CardField.NUMBER) },
                    onValueChange = {
                        val formatted = CardFieldValidators.formatCardNumber(it)
                        number = formatted.raw
                        brand = formatted.brand
                        onFieldValueChange()
                    },
                )
                Divider(color = theme.fieldDivider, thickness = 1.dp)
                Row(modifier = Modifier.height(theme.formFieldHeight)) {
                    ContainedFieldRow(
                        theme = theme,
                        value = expiry,
                        placeholder = Strings.text("placeholder.expDate", locale),
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        fieldHeight = theme.formFieldHeight,
                        modifier = Modifier.weight(1f),
                        visualTransformation = ExpiryVisualTransformation,
                        onFocus = { focused[CardField.EXPIRY] = true },
                        onBlur = { focused[CardField.EXPIRY] = false; onFieldBlur(CardField.EXPIRY) },
                        onValueChange = {
                            expiry = CardFieldValidators.normalizeExpiryDigits(it)
                            onFieldValueChange()
                        },
                    )
                    Divider(
                        color = theme.fieldDivider,
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                    )
                    ContainedFieldRow(
                        theme = theme,
                        value = cvv,
                        placeholder = Strings.text("placeholder.cvv", locale),
                        keyboardType = KeyboardType.Number,
                        imeAction = if (holderIsLast) ImeAction.Next else ImeAction.Done,
                        fieldHeight = theme.formFieldHeight,
                        modifier = Modifier.weight(1f),
                        trailing = {
                            Image(
                                painter = painterResource(R.drawable.xmoney_ic_lock),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(19.dp)
                                    .height(20.dp),
                                colorFilter = ColorFilter.tint(theme.mutedIcon),
                            )
                        },
                        onFocus = { focused[CardField.CVV] = true },
                        onBlur = { focused[CardField.CVV] = false; onFieldBlur(CardField.CVV) },
                        onValueChange = {
                            cvv = CardFieldValidators.normalizeDigits(it).take(4)
                            onFieldValueChange()
                        },
                    )
                }
                if (showHolderName && !holderFirst) {
                    Divider(color = theme.fieldDivider, thickness = 1.dp)
                    ContainedFieldRow(
                        theme = theme,
                        value = holder,
                        placeholder = Strings.text("placeholder.cardholderName", locale),
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        fieldHeight = theme.formFieldHeight,
                        onFocus = { focused[CardField.HOLDER] = true },
                        onBlur = { focused[CardField.HOLDER] = false; onFieldBlur(CardField.HOLDER) },
                        onValueChange = { holder = it; onFieldValueChange() },
                    )
                }
            }
        }

        if (!spacedInputs) {
            firstVisibleError?.let { field ->
                ErrorBadgeMessage(
                    message = errors[field]!!.localizedMessage(locale),
                    theme = theme,
                )
            }
        }

        if (showSaveOptIn) {
            SaveCardCheckbox(
                theme = theme,
                checked = save,
                label = saveCardLabel ?: Strings.text("elements.saveCard", locale),
                onToggle = {
                    save = !save
                    onFieldValueChange()
                },
            )
        }
        }
    }
}
