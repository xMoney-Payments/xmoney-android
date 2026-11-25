package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val DetailsFieldHeight = 48.dp
private val DetailsFieldInset = 16.dp
private val PhonePrefixWidth = 130.dp
private val FieldTracking = (-0.01).em

@Composable
internal fun PersonalDetailsForm(
    state: PersonalDetailsFormState,
    config: ResolvedPaymentConfig,
    theme: CheckoutTheme,
    onOpenPicker: (DetailsPicker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localeTag = config.options.locale
    val locale = remember(localeTag) { Locale.forLanguageTag(localeTag.replace('_', '-')) }
    val spaced = config.card.inputs.isSpaced
    val visibility = state.visibility
    val format = AddressFormats.forCountry(state.input.country)
    val openPicker = { kind: PickerKind ->
        onOpenPicker(pickerFor(kind, state, localeTag, locale, format.regionType))
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (spaced) 16.dp else 12.dp),
    ) {
            val showContact = visibility.showFullName || visibility.showPhone || visibility.showEmail
            if (showContact) {
                Column {
                    SectionHeading(Strings.text("personalDetails.contactInfoHeading", localeTag), theme, spaced)
                    val contactFields = CustomerDetailsValidator.contactFields(visibility)
                    if (spaced) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
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
                                SpacedPhoneRow(state, theme, localeTag, onPickCountry = { openPicker(PickerKind.PHONE_COUNTRY) })
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
                                if (started) Divider(color = theme.fieldBorder, thickness = 1.dp)
                                started = true
                                CondensedPhoneRow(state, theme, localeTag) { openPicker(PickerKind.PHONE_COUNTRY) }
                            }
                            if (visibility.showEmail) {
                                if (started) Divider(color = theme.fieldBorder, thickness = 1.dp)
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

            if (visibility.showAddressSection) {
                Column {
                    SectionHeading(Strings.text("personalDetails.addressHeading", localeTag), theme, spaced)
                    val addressFields = CustomerDetailsValidator.addressFields(visibility, state.input.country)
                    if (spaced) {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SpacedSelectField(
                                theme = theme,
                                label = Strings.text("personalDetails.country", localeTag),
                                value = countryLabel(state.input.country, locale),
                                placeholder = Strings.text("personalDetails.countryPlaceholder", localeTag),
                                field = PersonalField.COUNTRY,
                                state = state,
                                locale = localeTag,
                                onClick = { openPicker(PickerKind.COUNTRY) },
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
                                    onClick = { openPicker(PickerKind.REGION) },
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
                                onClick = { openPicker(PickerKind.COUNTRY) },
                            )
                            started = true
                            if (format.showRegion) {
                                Divider(color = theme.fieldBorder, thickness = 1.dp)
                                val keys = AddressFormats.regionKeys(format.regionType)
                                SelectRow(
                                    theme = theme,
                                    value = state.input.region.takeIf { it.isNotBlank() },
                                    placeholder = Strings.text(keys.selectPlaceholder, localeTag),
                                    onClick = { openPicker(PickerKind.REGION) },
                                )
                            }
                            if (started) Divider(color = theme.fieldBorder, thickness = 1.dp)
                            GroupTextField(
                                theme, state, PersonalField.ADDRESS,
                                Strings.text("personalDetails.addressPlaceholder", localeTag),
                                KeyboardType.Text,
                            )
                            Divider(color = theme.fieldBorder, thickness = 1.dp)
                            CondensedCityPostal(state, theme, localeTag, format.showPostal, format.postalType)
                        }
                        GroupError(addressFields, state, theme, localeTag)
                    }
                }
            }
    }
}

private fun pickerFor(
    kind: PickerKind,
    state: PersonalDetailsFormState,
    localeTag: String,
    locale: Locale,
    regionType: String?,
): DetailsPicker {
    return when (kind) {
        PickerKind.PHONE_COUNTRY -> DetailsPicker(
            title = Strings.text("personalDetails.phoneCountryCode", localeTag),
            options = Countries.sorted(locale).map { country ->
                PickerOption(country.code, Countries.phoneExpanded(country, locale))
            },
            selected = state.input.phoneCountry.takeIf { it.isNotBlank() },
            onSelect = { state.onPhoneCountrySelected(it) },
            onDismiss = { state.onBlur(PersonalField.PHONE_COUNTRY) },
        )
        PickerKind.COUNTRY -> DetailsPicker(
            title = Strings.text("personalDetails.country", localeTag),
            options = Countries.sorted(locale).map { country ->
                PickerOption(country.code, Countries.addressCompact(country, locale))
            },
            selected = state.input.country.takeIf { it.isNotBlank() },
            onSelect = { state.onCountrySelected(it) },
            onDismiss = { state.onBlur(PersonalField.COUNTRY) },
        )
        PickerKind.REGION -> DetailsPicker(
            title = Strings.text(AddressFormats.regionKeys(regionType).label, localeTag),
            options = Subdivisions.forCountry(state.input.country).map { PickerOption(it.name, it.name) },
            selected = state.input.region.takeIf { it.isNotBlank() },
            onSelect = { state.onRegionSelected(it) },
            onDismiss = { state.onBlur(PersonalField.REGION) },
        )
    }
}

@Composable
private fun SectionHeading(text: String, theme: CheckoutTheme, spaced: Boolean) {
    SheetText(
        text,
        theme = theme,
        color = if (spaced) theme.primaryText else theme.sectionLabel,
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
    fields.forEach { field ->
        val message = state.errorMessage(field, locale) ?: return@forEach
        ErrorBadgeMessage(message, theme, topPadding = 16.dp)
    }
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
        DetailsFieldCaption(label, theme)
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
        DetailsFieldCaption(label, theme)
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
    val prefix = phonePrefix(state.input.phoneCountry)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.width(PhonePrefixWidth)) {
            DetailsFieldCaption(Strings.text("personalDetails.phone", localeTag), theme)
            LabeledFieldBox(
                theme,
                borderColor(state, PersonalField.PHONE_COUNTRY, theme),
                borderWidth(state, PersonalField.PHONE_COUNTRY, theme),
            ) {
                SelectRow(
                    theme = theme,
                    value = prefix?.dial,
                    placeholder = Strings.text("personalDetails.phoneCountryPlaceholder", localeTag),
                    onClick = onPickCountry,
                    leading = prefix?.flag,
                )
            }
            FieldErrorText(state.errorMessage(PersonalField.PHONE_COUNTRY, localeTag), theme)
        }
        Column(modifier = Modifier.weight(1f)) {
            DetailsFieldCaption(" ", theme)
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
    val postalLabel = Strings.text(postal.label, localeTag)
    val postalPlaceholder = Strings.text(postal.placeholder, localeTag)
    val postalValue = state.input.postalCode
    val postalTextWidth = measuredTextWidth(
        postalValue.ifBlank { postalPlaceholder },
        theme,
        18f,
        if (postalValue.isBlank()) FontWeight.Normal else FontWeight.Medium,
    )
    val postalLabelWidth = measuredTextWidth(postalLabel, theme, 14f, FontWeight.SemiBold)
    val postalWidth = maxOf(postalLabelWidth, postalTextWidth + DetailsFieldInset * 2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SpacedTextField(
                theme, Strings.text("personalDetails.city", localeTag),
                Strings.text("personalDetails.cityPlaceholder", localeTag),
                state.input.city, PersonalField.CITY, KeyboardType.Text, state, localeTag,
            )
        }
        Column(modifier = Modifier.width(postalWidth)) {
            SpacedTextField(
                theme, postalLabel, postalPlaceholder,
                postalValue, PersonalField.POSTAL, KeyboardType.Text, state, localeTag,
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
    val prefix = phonePrefix(state.input.phoneCountry)
    Row(modifier = Modifier.height(DetailsFieldHeight)) {
        SelectRow(
            theme = theme,
            value = prefix?.dial,
            placeholder = Strings.text("personalDetails.phoneCountryPlaceholder", localeTag),
            onClick = onPickCountry,
            leading = prefix?.flag,
            fill = false,
            modifier = Modifier.width(PhonePrefixWidth),
        )
        Divider(
            color = theme.fieldBorder,
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
    Row(modifier = Modifier.height(DetailsFieldHeight)) {
        GroupTextField(
            theme, state, PersonalField.CITY,
            Strings.text("personalDetails.cityPlaceholder", localeTag),
            KeyboardType.Text,
            modifier = Modifier.weight(1f),
        )
        Divider(
            color = theme.fieldBorder,
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
        fieldHeight = DetailsFieldHeight,
        modifier = modifier,
        horizontalPadding = DetailsFieldInset,
        valueColor = theme.primaryText,
        placeholderColor = theme.inputPlaceholder,
        valueWeight = FontWeight.Medium,
        placeholderWeight = FontWeight.Normal,
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
    leading: String? = null,
    fill: Boolean = true,
) {
    val label = value ?: placeholder
    val filled = value != null
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(DetailsFieldHeight)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = DetailsFieldInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!leading.isNullOrEmpty()) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SheetText(leading, theme = theme, fontSize = 18f, maxLines = 1)
            }
            Box(Modifier.width(8.dp))
        }
        SheetText(
            label,
            theme = theme,
            color = if (filled) theme.primaryText else theme.inputPlaceholder,
            fontSize = 18f,
            fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal,
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

@Composable
private fun DetailsFieldCaption(text: String, theme: CheckoutTheme) {
    SheetText(
        text,
        theme = theme,
        color = theme.sectionLabel,
        fontSize = 14f,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = FieldTracking,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun measuredTextWidth(
    text: String,
    theme: CheckoutTheme,
    fontSize: Float,
    weight: FontWeight,
): Dp {
    val measurer = rememberTextMeasurer()
    val widthPx = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = theme.scaledSp(fontSize),
            fontWeight = weight,
            fontFamily = theme.fontFamily,
        ),
        maxLines = 1,
    ).size.width
    return with(LocalDensity.current) { widthPx.toDp() }
}

private fun countryLabel(code: String, locale: Locale): String? {
    val country = Countries.byCode(code) ?: return null
    return Countries.localizedName(country, locale)
}

private data class PhonePrefix(val flag: String, val dial: String)

private fun phonePrefix(code: String): PhonePrefix? {
    val country = Countries.byCode(code) ?: return null
    val flag = Countries.flagEmoji(country.code)
    if (flag.isEmpty()) return null
    return PhonePrefix(flag, "+${country.dialCode}")
}
