package com.xmoney.paymentelement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.xmoney.paymentelement.R
import com.xmoney.paymentelement.theme.CheckoutTheme
import com.xmoney.payments.config.Strings

internal data class PickerOption(
    val value: String,
    val label: String,
)

internal class DetailsPicker(
    val title: String,
    val options: List<PickerOption>,
    val selected: String?,
    val onSelect: (String) -> Unit,
    val onDismiss: () -> Unit,
)

/** Same chrome as the sheet: close X at the sheet's top-left spot, title beside it. */
@Composable
internal fun SelectionListHeader(
    title: String,
    theme: CheckoutTheme,
    locale: String,
    enabled: Boolean,
    startPadding: Dp = 6.dp,
    onClose: () -> Unit,
) {
    val closeLabel = Strings.text("sheet.cancel", locale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = enabled, onClick = onClose)
                .semantics {
                    contentDescription = closeLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xmoney_ic_close),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(theme.primaryText),
            )
        }
        SheetText(
            title,
            theme = theme,
            fontSize = 21f,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.03).em,
            lineHeight = 21f,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .semantics { heading() },
        )
    }
}

@Composable
internal fun SelectionList(
    title: String,
    options: List<PickerOption>,
    selected: String?,
    theme: CheckoutTheme,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val needle = query.trim()
        if (needle.isEmpty()) options else options.filter { it.label.contains(needle, ignoreCase = true) }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        SearchBox(theme = theme, query = query, placeholder = title, onQueryChange = { query = it })
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filtered, key = { it.value }) { option ->
                ChoiceRow(
                    theme = theme,
                    label = option.label,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun SearchBox(
    theme: CheckoutTheme,
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(theme.formFieldRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(theme.componentBackground)
            .border(1.dp, theme.fieldBorder, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.xmoney_ic_search),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(theme.metaText),
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = theme.primaryText,
                fontSize = theme.scaledSp(18f),
                fontFamily = theme.fontFamily,
                letterSpacing = (-0.01).em,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            cursorBrush = SolidColor(theme.primary),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        SheetText(
                            placeholder,
                            theme = theme,
                            color = theme.inputPlaceholder,
                            fontSize = 18f,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ChoiceRow(
    theme: CheckoutTheme,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(theme.brandTileRadius))
            .background(if (selected) theme.componentBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetText(
            label,
            theme = theme,
            fontSize = 18f,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        RadioDot(theme, selected)
    }
}

/** Embedded checkout. The payment sheet hosts the same list in place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionListSheet(
    picker: DetailsPicker,
    theme: CheckoutTheme,
    locale: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            SelectionListHeader(
                title = picker.title,
                theme = theme,
                locale = locale,
                enabled = enabled,
                onClose = onDismiss,
            )
            SelectionList(
                title = picker.title,
                options = picker.options,
                selected = picker.selected,
                theme = theme,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = SheetContentHorizontalPadding)
                    .padding(top = 16.dp),
                onSelect = onSelect,
            )
        }
    }
}
