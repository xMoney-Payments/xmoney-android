package com.xmoney.paymentelement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xmoney.paymentelement.theme.CheckoutTheme

internal data class PickerOption(
    val value: String,
    val label: String,
)

@Composable
internal fun OptionPickerDialog(
    title: String,
    options: List<PickerOption>,
    theme: CheckoutTheme,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            options
        } else {
            options.filter { it.label.contains(needle, ignoreCase = true) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.formFieldRadius))
                .background(theme.background)
                .padding(16.dp),
        ) {
            SheetText(
                title,
                theme = theme,
                fontSize = 16f,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LabeledFieldBox(
                theme = theme,
                borderColor = theme.fieldBorder,
                borderWidth = theme.borderWidth,
            ) {
                ContainedFieldRow(
                    theme = theme,
                    value = query,
                    placeholder = title,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                    fieldHeight = theme.formFieldHeight,
                    onValueChange = { query = it },
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(top = 8.dp),
            ) {
                items(filtered, key = { it.value }) { option ->
                    SheetText(
                        option.label,
                        theme = theme,
                        fontSize = 16f,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option.value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    )
                    Divider(color = theme.fieldDivider)
                }
            }
        }
    }
}
