package com.xmoney.example.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xmoney.example.theme.ExampleColors

@Composable
fun PlaygroundPresetSwatches(
    selectedPresetId: String?,
    onPresetSelected: (DemoAppearancePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DemoAppearancePresets.all.forEach { preset ->
            val selected = selectedPresetId == preset.id
            val accent = preset.appearance.brandColor?.light?.let { Color(it) } ?: ExampleColors.Purple
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clickable { onPresetSelected(preset) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) ExampleColors.Purple else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
