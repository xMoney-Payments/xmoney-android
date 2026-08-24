package com.xmoney.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xmoney.example.BuildConfig
import com.xmoney.example.SAMPLE_AMOUNT_MINOR
import com.xmoney.example.formatMoney

@Composable
fun SampleScaffold(
    title: String,
    subtitle: String,
    activity: ComponentActivity,
    scrollable: Boolean = true,
    showTestCards: Boolean = false,
    nameCheckHint: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                ExampleTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBack = { activity.finish() },
                    actions = {
                        if (showTestCards) {
                            TestCardsAction(nameCheckHint = nameCheckHint)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun SampleOrderCard(
    description: String = BuildConfig.DESCRIPTION.ifBlank { "Checkout item" },
    amountMinor: Long = SAMPLE_AMOUNT_MINOR,
    currency: String = BuildConfig.CURRENCY,
) {
    ExampleCard {
        Text(
            text = "ORDER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Text(
            text = formatMoney(amountMinor, currency),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
