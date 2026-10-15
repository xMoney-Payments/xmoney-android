package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.ResolvedPaymentConfig
import com.xmoney.payments.config.Strings
import com.xmoney.payments.customerdetails.AddressFormats
import com.xmoney.payments.customerdetails.Countries
import com.xmoney.payments.customerdetails.CustomerDetailsValidator
import com.xmoney.payments.customerdetails.PersonalField
import com.xmoney.payments.customerdetails.Subdivisions
import java.util.Locale

private enum class PickerKind { PHONE_COUNTRY, COUNTRY, REGION }

private val DetailsPanelRadius = 15.dp
private val FieldTracking = (-0.01).em

@Composable
internal fun PersonalDetailsForm(
    state: PersonalDetailsFormState,
    config: ResolvedPaymentConfig,
    theme: CheckoutTheme,
) {
    val localeTag = config.options.locale
    val locale = remember(localeTag) { Locale.forLanguageTag(localeTag.replace('_', '-')) }
    val spaced = config.card.inputs.isSpaced
    val visibility = state.visibility
    val format = AddressFormats.forCountry(state.input.country)
    var picker by remember { mutableStateOf<PickerKind?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DetailsPanelRadius))
                .background(theme.selectedBackground)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val showContact = visibility.showFullName || visibility.showPhone || visibility.showEmail
            if (showContact) {
                Column {
                    SectionHeading(Strings.text("personalDetails.contactInfoHeading", localeTag), theme, spaced)
                    val contactFields = CustomerDetailsValidator.contactFields(visibility)
                    if (spaced) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (visibility.showFullName) {
                                SpacedTextField(
                                    theme = theme,
                                    label = Strings.text("personalDetails.fullName", localeTag),
                                    placeholder = Strings.text("personalDetails.fullNamePlaceholder", localeTag),
                                    value = state.input.fullName,
                                    field = PersonalField.FULL_NAME,
                                    keyboardType = KeyboardType.Text,
                                    state = state,
                                    locale = localeTag,
                                )
                            }
                            if (visibility.showPhone) {
                                SpacedPhoneRow(state, theme, localeTag, onPickCountry = { picker = PickerKind.PHONE_COUNTRY })
                            }
                            if (visibility.showEmail) {
                                SpacedTextField(
                                    theme = theme,
                                    label = Strings.text("personalDetails.email", localeTag),
                                    placeholder = Strings.text("personalDetails.emailPlaceholder", localeTag),
                                    value = state.input.email,
                                    field = PersonalField.EMAIL,
                                    keyboardType = KeyboardType.Email,
                                    state = state,
                                    locale = localeTag,
                                )
                            }
                        }
                    } else {
                        GroupedFields(
                            theme = theme,
                            fields = contactFields,
                            state = state,
                        ) {
                            var started = false
                            if (visibility.showFullName) {
                                started = true
                                GroupTextField(
                                    theme, state, PersonalField.FULL_NAME,
                                    Strings.text("personalDetails.fullNamePlaceholder", localeTag),
                                    KeyboardType.Text,
                                )
                            }
                            if (visibility.showPhone) {
                                if (started) Divider(color = theme.fieldDivider, thickness = 1.dp)
                                started = true
                                CondensedPhoneRow(state, theme, localeTag) { picker = PickerKind.PHONE_COUNTRY }
                            }
                            if (visibility.showEmail) {
                                if (started) Divider(color = theme.fieldDivider, thickness = 1.dp)
                                GroupTextField(
                                    theme, state, PersonalField.EMAIL,
                                    Strings.text("personalDetails.emailPlaceholder", localeTag),
                                    KeyboardType.Email,
                                )
                            }
                        }
                        GroupError(contactFields, state, theme, localeTag)
                    }
                }
            }

            if (spaced && showContact && visibility.showAddressSection) {
                Divider(color = theme.fieldDivider, thickness = 1.dp)
            }

            if (visibility.showAddressSection) {
                Column {
                    SectionHeading(Strings.text("personalDetails.addressHeading", localeTag), theme, spaced)
                    val addressFields = CustomerDetailsValidator.addressFields(visibility, state.input.country)
                    if (spaced) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SpacedSelectField(
                                theme = theme,
                                label = Strings.text("personalDetails.country", localeTag),
                                value = countryLabel(state.input.country, locale),
                                placeholder = Strings.text("personalDetails.countryPlaceholder", localeTag),
                                field = PersonalField.COUNTRY,
                                state = state,
                                locale = localeTag,
                                onClick = { picker = PickerKind.COUNTRY },
                            )
                            if (format.showRegion) {
                                val keys = AddressFormats.regionKeys(format.regionType)
                                SpacedSelectField(
                                    theme = theme,
                                    label = Strings.text(keys.label, localeTag),
                                    value = state.input.region.takeIf { it.isNotBlank() },
                                    placeholder = Strings.text(keys.selectPlaceholder, localeTag),
                                    field = PersonalField.REGION,
                                    state = state,
                                    locale = localeTag,
                                    onClick = { picker = PickerKind.REGION },
                                )
                            }
                            SpacedTextField(
                                theme = theme,
                                label = Strings.text("personalDetails.address", localeTag),
                                placeholder = Strings.text("personalDetails.addressPlaceholder", localeTag),
                                value = state.input.address,
                                field = PersonalField.ADDRESS,
                                keyboardType = KeyboardType.Text,
                                state = state,
                                locale = localeTag,
                            )
                            SpacedCityPostal(state, theme, localeTag, format.showPostal, format.postalType)
                        }
                    } else {
                        GroupedFields(theme, addressFields, state) {
                            var started = false
                            SelectRow(
                                theme = theme,
                                value = countryLabel(state.input.country, locale),
                                placeholder = Strings.text("personalDetails.countryPlaceholder", localeTag),
                                onClick = { picker = PickerKind.COUNTRY },
                            )
                            started = true
                            if (format.showRegion) {
                                Divider(color = theme.fieldDivider, thickness = 1.dp)
                                val keys = AddressFormats.regionKeys(format.regionType)
                                SelectRow(
                                    theme = theme,
                                    value = state.input.region.takeIf { it.isNotBlank() },
                                    placeholder = Strings.text(keys.selectPlaceholder, localeTag),
                                    onClick = { picker = PickerKind.REGION },
                                )
                            }
                            if (started) Divider(color = theme.fieldDivider, thickness = 1.dp)
                            GroupTextField(
                                theme, state, PersonalField.ADDRESS,
                                Strings.text("personalDetails.addressPlaceholder", localeTag),
                                KeyboardType.Text,
                            )
                            Divider(color = theme.fieldDivider, thickness = 1.dp)
                            CondensedCityPostal(state, theme, localeTag, format.showPostal, format.postalType)
                        }
                        GroupError(addressFields, state, theme, localeTag)
                    }
                }
            }
        }
    }

    when (picker) {
        PickerKind.PHONE_COUNTRY -> OptionPickerDialog(
            title = Strings.text("personalDetails.phoneCountryPlaceholder", localeTag),
            options = remember(localeTag) {
                Countries.sorted(locale).map { country ->
                    PickerOption(country.code, Countries.phoneExpanded(country, locale))
                }
            },
            theme = theme,
            onDismiss = {
                state.onBlur(PersonalField.PHONE_COUNTRY)
                picker = null
            },
            onSelect = { state.onPhoneCountrySelected(it) },
        )
        PickerKind.COUNTRY -> OptionPickerDialog(
            title = Strings.text("personalDetails.countryPlaceholder", localeTag),
            options = remember(localeTag) {
                Countries.sorted(locale).map { country ->
                    PickerOption(country.code, Countries.addressCompact(country, locale))
                }
            },
            theme = theme,
            onDismiss = {
                state.onBlur(PersonalField.COUNTRY)
                picker = null
            },
            onSelect = { state.onCountrySelected(it) },
        )
        PickerKind.REGION -> OptionPickerDialog(
            title = Strings.text(AddressFormats.regionKeys(format.regionType).selectPlaceholder, localeTag),
            options = Subdivisions.forCountry(state.input.country).map { PickerOption(it.name, it.name) },
            theme = theme,
            onDismiss = {
                state.onBlur(PersonalField.REGION)
                picker = null
            },
            onSelect = { state.onRegionSelected(it) },
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeading(text: String, theme: CheckoutTheme, spaced: Boolean) {
    SheetText(
        text,
        theme = theme,
        color = if (spaced) theme.primaryText else theme.icon,
        fontSize = if (spaced) 16f else 14f,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = FieldTracking,
        lineHeight = if (spaced) 24f else 16f,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupedFields(
    theme: CheckoutTheme,
    fields: List<PersonalField>,
    state: PersonalDetailsFormState,
    content: @Composable () -> Unit,
) {
    val hasError = fields.any { state.shouldShow(it) }
    val anyFocused = fields.any { state.focused[it] == true }
    val borderColor = when {
        hasError -> theme.errorBorder
        anyFocused -> theme.primary
        else -> theme.fieldBorder
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.formFieldRadius))
            .background(theme.componentBackground)
            .border(theme.fieldStrokeWidth(hasError), borderColor, RoundedCornerShape(theme.formFieldRadius)),
        content = { content() },
    )
}

@Composable
private fun GroupError(
    fields: List<PersonalField>,
    state: PersonalDetailsFormState,
    theme: CheckoutTheme,
    locale: String,
) {
    val message = fields.firstNotNullOfOrNull { state.errorMessage(it, locale) }
    if (message != null) ErrorBadgeMessage(message, theme)
}

@Composable
private fun SpacedTextField(
    theme: CheckoutTheme,
    label: String,
    placeholder: String,
    value: String,
    field: PersonalField,
    keyboardType: KeyboardType,
    state: PersonalDetailsFormState,
    locale: String,
) {
    Column {
        FieldLabel(label, theme)
        LabeledFieldBox(theme, borderColor(state, field, theme), borderWidth(state, field, theme)) {
            GroupTextField(theme, state, field, placeholder, keyboardType)
        }
        FieldErrorText(state.errorMessage(field, locale), theme)
    }
}

@Composable
private fun SpacedSelectField(
    theme: CheckoutTheme,
    label: String,
    value: String?,
    placeholder: String,
    field: PersonalField,
    state: PersonalDetailsFormState,
    locale: String,
    onClick: () -> Unit,
) {
    Column {
        FieldLabel(label, theme)
        LabeledFieldBox(theme, borderColor(state, field, theme), borderWidth(state, field, theme)) {
            SelectRow(theme, value, placeholder, onClick)
        }
        FieldErrorText(state.errorMessage(field, locale), theme)
    }
}

@Composable
private fun SpacedPhoneRow(
    state: PersonalDetailsFormState,
    theme: CheckoutTheme,
    localeTag: String,
    onPickCountry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(130f)) {
            FieldLabel(Strings.text("personalDetails.phoneCountryCode", localeTag), theme)
            LabeledFieldBox(
                theme,
                borderColor(state, PersonalField.PHONE_COUNTRY, theme),
                borderWidth(state, PersonalField.PHONE_COUNTRY, theme),
            ) {
                SelectRow(
                    theme = theme,
                    value = phoneClosedLabel(state.input.phoneCountry),
                    placeholder = Strings.text("personalDetails.phoneCountryPlaceholder", localeTag),
                    onClick = onPickCountry,
                )
            }
            FieldErrorText(state.errorMessage(PersonalField.PHONE_COUNTRY, localeTag), theme)
        }
        Column(modifier = Modifier.weight(204f)) {
            FieldLabel(Strings.text("personalDetails.phone", localeTag), theme)
            LabeledFieldBox(
                theme,
                borderColor(state, PersonalField.PHONE, theme),
                borderWidth(state, PersonalField.PHONE, theme),
            ) {
                GroupTextField(
                    theme, state, PersonalField.PHONE,
                    Strings.text("personalDetails.phonePlaceholder", localeTag),
                    KeyboardType.Phone,
                )
            }
            FieldErrorText(state.errorMessage(PersonalField.PHONE, localeTag), theme)
        }
    }
}

@Composable
private fun SpacedCityPostal(
    state: PersonalDetailsFormState,
    theme: CheckoutTheme,
    localeTag: String,
    showPostal: Boolean,
    postalType: String,
) {
    val postal = AddressFormats.postalKeys(postalType)
    if (!showPostal) {
        SpacedTextField(
            theme, Strings.text("personalDetails.city", localeTag),
            Strings.text("personalDetails.cityPlaceholder", localeTag),
            state.input.city, PersonalField.CITY, KeyboardType.Text, state, localeTag,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(191f)) {
            SpacedTextField(
                theme, Strings.text("personalDetails.city", localeTag),
                Strings.text("personalDetails.cityPlaceholder", localeTag),
                state.input.city, PersonalField.CITY, KeyboardType.Text, state, localeTag,
            )
        }
        Column(modifier = Modifier.weight(127f)) {
            SpacedTextField(
                theme, Strings.text(postal.label, localeTag),
                Strings.text(postal.placeholder, localeTag),
                state.input.postalCode, PersonalField.POSTAL, KeyboardType.Text, state, localeTag,
            )
        }
    }
}

@Composable
private fun CondensedPhoneRow(
    state: PersonalDetailsFormState,
    theme: CheckoutTheme,
    localeTag: String,
    onPickCountry: () -> Unit,
) {
    Row(modifier = Modifier.height(theme.formFieldHeight)) {
        SelectRow(
            theme = theme,
            value = phoneClosedLabel(state.input.phoneCountry),
            placeholder = Strings.text("personalDetails.phoneCountryPlaceholder", localeTag),
            onClick = onPickCountry,
            modifier = Modifier.weight(130f),
        )
        Divider(
            color = theme.fieldDivider,
            modifier = Modifier.fillMaxHeight().width(1.dp),
        )
        GroupTextField(
            theme, state, PersonalField.PHONE,
            Strings.text("personalDetails.phonePlaceholder", localeTag),
            KeyboardType.Phone,
            modifier = Modifier.weight(204f),
        )
    }
}

@Composable
private fun CondensedCityPostal(
    state: PersonalDetailsFormState,
    theme: CheckoutTheme,
    localeTag: String,
    showPostal: Boolean,
    postalType: String,
) {
    if (!showPostal) {
        GroupTextField(
            theme, state, PersonalField.CITY,
            Strings.text("personalDetails.cityPlaceholder", localeTag),
            KeyboardType.Text,
        )
        return
    }
    val postal = AddressFormats.postalKeys(postalType)
    Row(modifier = Modifier.height(theme.formFieldHeight)) {
        GroupTextField(
            theme, state, PersonalField.CITY,
            Strings.text("personalDetails.cityPlaceholder", localeTag),
            KeyboardType.Text,
            modifier = Modifier.weight(1f),
        )
        Divider(
            color = theme.fieldDivider,
            modifier = Modifier.fillMaxHeight().width(1.dp),
        )
        GroupTextField(
            theme, state, PersonalField.POSTAL,
            Strings.text(postal.placeholder, localeTag),
            KeyboardType.Text,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GroupTextField(
    theme: CheckoutTheme,
    state: PersonalDetailsFormState,
    field: PersonalField,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val value = when (field) {
        PersonalField.FULL_NAME -> state.input.fullName
        PersonalField.EMAIL -> state.input.email
        PersonalField.PHONE -> state.input.phoneNational
        PersonalField.ADDRESS -> state.input.address
        PersonalField.CITY -> state.input.city
        PersonalField.POSTAL -> state.input.postalCode
        else -> ""
    }
    ContainedFieldRow(
        theme = theme,
        value = value,
        placeholder = placeholder,
        keyboardType = keyboardType,
        fieldHeight = theme.formFieldHeight,
        modifier = modifier,
        imeAction = ImeAction.Next,
        onFocus = { state.focused[field] = true },
        onBlur = {
            state.focused[field] = false
            state.onBlur(field)
        },
        onValueChange = { state.onTextChange(field, it) },
    )
}

@Composable
private fun SelectRow(
    theme: CheckoutTheme,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = value ?: placeholder
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(theme.formFieldHeight)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SheetText(
            label,
            theme = theme,
            color = if (value == null) theme.placeholderText else theme.componentText,
            fontSize = 18f,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { },
        )
        Image(
            painter = painterResource(R.drawable.xmoney_ic_chevron_down),
            contentDescription = null,
            modifier = Modifier
                .size(width = 14.dp, height = 9.dp)
                .clearAndSetSemantics { },
            colorFilter = ColorFilter.tint(theme.mutedIcon),
        )
    }
}

private fun borderColor(state: PersonalDetailsFormState, field: PersonalField, theme: CheckoutTheme): Color =
    when {
        state.shouldShow(field) -> theme.errorBorder
        state.focused[field] == true -> theme.primary
        else -> theme.fieldBorder
    }

private fun borderWidth(state: PersonalDetailsFormState, field: PersonalField, theme: CheckoutTheme) =
    theme.fieldStrokeWidth(state.shouldShow(field))

private fun countryLabel(code: String, locale: Locale): String? {
    val country = Countries.byCode(code) ?: return null
    return Countries.addressCompact(country, locale)
}

private fun phoneClosedLabel(code: String): String? {
    val country = Countries.byCode(code) ?: return null
    return Countries.phoneCompact(country)
}
