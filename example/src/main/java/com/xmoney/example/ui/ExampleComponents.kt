package com.xmoney.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.layout.ContentScale
import com.xmoney.example.theme.ExampleColors
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.theme.ExampleThemeController
import com.xmoney.example.theme.LocalBrandAccent
import com.xmoney.example.theme.LocalBrandAccentText
import com.xmoney.example.theme.LocalBrandOnAccent
import com.xmoney.example.theme.LocalExampleSemantics
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentelement.R as PaymentElementR

enum class ExampleButtonVariant { Primary, Secondary }

@Composable
fun ExampleCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ExampleRadii.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun ExampleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: ExampleButtonVariant = ExampleButtonVariant.Primary,
) {
    val isPrimary = variant == ExampleButtonVariant.Primary
    val accent = LocalBrandAccent.current
    val onAccent = LocalBrandOnAccent.current
    val container = if (isPrimary) accent else MaterialTheme.colorScheme.surface
    val content = if (isPrimary) onAccent else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(ExampleRadii.pill)
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isPrimary && enabled && !loading) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = accent.copy(alpha = 0.28f),
                        spotColor = accent.copy(alpha = 0.38f),
                    )
                } else {
                    Modifier
                },
            )
            .height(52.dp),
        shape = shape,
        border = if (isPrimary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.45f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (loading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
                Text("Processing…", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ExampleStatusChip(
    text: String,
    kind: ExampleStatusKind,
    modifier: Modifier = Modifier,
) {
    val semantics = LocalExampleSemantics.current
    val (container, content) = when (kind) {
        ExampleStatusKind.Success -> semantics.successSoft to semantics.success
        ExampleStatusKind.Error -> semantics.dangerSoft to MaterialTheme.colorScheme.error
        ExampleStatusKind.Neutral -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ExampleRadii.inner))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

enum class ExampleStatusKind { Success, Error, Neutral }

@Composable
fun ExampleResultPanel(
    result: PaymentResult,
    modifier: Modifier = Modifier,
    fallbackAmount: String? = null,
    successTitle: String = "Payment complete",
    failureTitle: String = "Payment didn’t go through",
    canceledTitle: String = "Payment canceled",
) {
    val (title, subtitle, icon, iconTint, iconBg) = when (result) {
        is PaymentResult.Complete -> ResultHero(
            title = successTitle,
            subtitle = "Your payment went through.",
            icon = Icons.Outlined.Check,
            iconTint = ExampleColors.Purple,
            iconBg = ExampleColors.Purple.copy(alpha = 0.12f),
        )
        is PaymentResult.Failed -> ResultHero(
            title = failureTitle,
            subtitle = "Something went wrong. You can try again.",
            icon = Icons.Outlined.Close,
            iconTint = MaterialTheme.colorScheme.error,
            iconBg = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        )
        PaymentResult.Canceled -> ResultHero(
            title = canceledTitle,
            subtitle = "You closed checkout before finishing.",
            icon = Icons.Outlined.Remove,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconBg = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        ExampleCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            Column {
                resultRows(result, fallbackAmount).forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                    ExampleKeyValueRow(
                        label = row.label,
                        value = row.value,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private data class ResultHero(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
)

private data class ResultRow(val label: String, val value: String)

private fun resultRows(result: PaymentResult, fallbackAmount: String?): List<ResultRow> {
    return when (result) {
        is PaymentResult.Complete -> {
            val tx = result.transaction
            val customer = tx.customerData
            val name = listOfNotNull(customer?.firstName, customer?.lastName)
                .joinToString(" ")
                .ifBlank { null }
            buildList {
                add(ResultRow("Status", tx.status?.takeIf { it.isNotBlank() } ?: "Complete"))
                formatAmount(tx.amount, tx.currencyKey, fallbackAmount)?.let {
                    add(ResultRow("Amount", it))
                }
                tx.amountInEuro?.takeIf { it.isNotBlank() }?.let {
                    add(ResultRow("Amount in EUR", "€$it"))
                }
                tx.id?.takeIf { it.isNotBlank() }?.let { add(ResultRow("Transaction", it)) }
                tx.externalOrderId?.takeIf { it.isNotBlank() }?.let {
                    add(ResultRow("External order", it))
                }
                tx.description?.takeIf { it.isNotBlank() }?.let {
                    add(ResultRow("Description", it))
                }
                name?.let { add(ResultRow("Customer", it)) }
                customer?.email?.takeIf { it.isNotBlank() }?.let { add(ResultRow("Email", it)) }
            }
        }
        is PaymentResult.Failed -> buildList {
            add(ResultRow("Status", "Failed"))
            add(ResultRow("Error code", result.error.code))
            add(ResultRow("Message", result.error.merchantMessage()))
        }
        PaymentResult.Canceled -> listOf(
            ResultRow("Status", "Canceled"),
            ResultRow("Message", "No charge was made."),
        )
    }
}

private fun formatAmount(amount: String?, currency: String?, fallback: String?): String? {
    val raw = amount?.takeIf { it.isNotBlank() }
    if (raw == null) return fallback
    val cur = currency?.uppercase().orEmpty()
    return when (cur) {
        "EUR" -> "€$raw"
        "USD" -> "$$raw"
        "GBP" -> "£$raw"
        "" -> raw
        else -> "$raw $cur"
    }
}

@Composable
fun ExampleKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Merchant loading chrome for the **initial** bind. Always composes [content]
 * so `PaymentElement` / `GooglePayButton` can emit `Ready`. After the first
 * Ready, the surface stays visible — `updateOrder` must not hide it.
 */
@Composable
fun MerchantReadyGate(
    ready: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hasBound by remember { mutableStateOf(ready) }
    SideEffect {
        if (ready) hasBound = true
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = if (hasBound) Modifier.fillMaxWidth() else Modifier.size(0.dp)) {
            content()
        }
        if (!hasBound) {
            ExampleLoader(message = message)
        }
    }
}

@Composable
fun ExampleLoader(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    message: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            strokeWidth = 2.5.dp,
            color = ExampleColors.Purple,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ExampleTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    showWordmark: Boolean = false,
    showThemeToggle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showWordmark) {
            ExampleWordmark()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            if (showThemeToggle) {
                ExampleThemeToggle()
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ExampleThemeToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = ExampleThemeController.isDark(systemDark)
    IconButton(
        onClick = { ExampleThemeController.toggle(context, systemDark) },
        modifier = modifier.size(44.dp),
    ) {
        Icon(
            imageVector = if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun ExampleWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(PaymentElementR.drawable.xmoney_ic_wordmark),
        contentDescription = "xMoney",
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        modifier = modifier.height(18.dp),
    )
}

@Composable
fun ExampleProductPhoto(
    imageRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
fun ExampleAddChip(
    quantity: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filled = quantity > 0
    val accent = LocalBrandAccent.current
    val accentText = LocalBrandAccentText.current
    val onAccent = LocalBrandOnAccent.current
    val shape = RoundedCornerShape(ExampleRadii.pill)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) accent.copy(alpha = 0.12f) else accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (filled) quantity.toString() else "Add",
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) accentText else onAccent,
        )
    }
}

@Composable
fun ExampleCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ExampleToggleRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ExampleRadii.inner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
fun ExampleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val subtitleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ExampleColors.Purple,
                    checkedThumbColor = Color.White,
                ),
            )
        }
    }
}
