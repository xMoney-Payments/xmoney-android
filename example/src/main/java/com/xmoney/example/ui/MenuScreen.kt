package com.xmoney.example.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xmoney.example.R
import com.xmoney.example.playground.PlaygroundActivity
import com.xmoney.example.samples.CardHolderVerificationSampleActivity
import com.xmoney.example.samples.EmbeddedPaymentSampleActivity
import com.xmoney.example.samples.EmbeddedViewsSampleActivity
import com.xmoney.example.samples.GooglePayActivitySample
import com.xmoney.example.samples.GooglePaySampleActivity
import com.xmoney.example.samples.MerchantPayButtonSampleActivity
import com.xmoney.example.samples.PaymentSheetActivitySample
import com.xmoney.example.samples.PaymentSheetSampleActivity
import com.xmoney.example.samples.UpdateOrderSampleActivity
import com.xmoney.example.scenarios.hearth.HearthActivity
import com.xmoney.example.scenarios.pulse.PulseActivity
import com.xmoney.example.scenarios.shop.ShopActivity

private data class MenuItem(
    val title: String,
    val subtitle: String,
    val klass: Class<*>,
    val section: MenuSection,
    val imageRes: Int? = null,
)

private enum class MenuSection(val label: String, val muted: Boolean = false) {
    Integrations("Integrations"),
    ExampleApp("Example app"),
    Advanced("Advanced"),
    Internal("Internal", muted = true),
}

@Composable
fun MenuScreen() {
    val items = listOf(
        MenuItem(
            title = "Payment Sheet",
            subtitle = "Drop-in checkout sheet — copy-paste starting point",
            klass = PaymentSheetSampleActivity::class.java,
            section = MenuSection.Integrations,
        ),
        MenuItem(
            title = "Embedded Payment Element",
            subtitle = "Card, saved cards, and Google Pay in your layout",
            klass = EmbeddedPaymentSampleActivity::class.java,
            section = MenuSection.Integrations,
        ),
        MenuItem(
            title = "Google Pay",
            subtitle = "Standalone wallet button",
            klass = GooglePaySampleActivity::class.java,
            section = MenuSection.Integrations,
        ),
        MenuItem(
            title = "Lumen shop",
            subtitle = "Lifestyle store — catalog, cart, Payment Sheet",
            klass = ShopActivity::class.java,
            section = MenuSection.ExampleApp,
            imageRes = R.drawable.product_earbuds,
        ),
        MenuItem(
            title = "Hearth Café",
            subtitle = "Food menu — cart and Embedded checkout",
            klass = HearthActivity::class.java,
            section = MenuSection.ExampleApp,
            imageRes = R.drawable.product_espresso,
        ),
        MenuItem(
            title = "Pulse Studio",
            subtitle = "Memberships and classes — Embedded checkout",
            klass = PulseActivity::class.java,
            section = MenuSection.ExampleApp,
            imageRes = R.drawable.product_pulse_unlimited,
        ),
        MenuItem(
            title = "Payment Sheet (Activity API)",
            subtitle = "Imperative present() from a FragmentActivity",
            klass = PaymentSheetActivitySample::class.java,
            section = MenuSection.Advanced,
        ),
        MenuItem(
            title = "Google Pay (Activity API)",
            subtitle = "GooglePay(config).present() / updateOrder()",
            klass = GooglePayActivitySample::class.java,
            section = MenuSection.Advanced,
        ),
        MenuItem(
            title = "Merchant Pay button",
            subtitle = "Embedded form, your CTA via confirm()",
            klass = MerchantPayButtonSampleActivity::class.java,
            section = MenuSection.Advanced,
        ),
        MenuItem(
            title = "Update order",
            subtitle = "updateOrder() a new PaymentIntent on a mounted Element",
            klass = UpdateOrderSampleActivity::class.java,
            section = MenuSection.Advanced,
        ),
        MenuItem(
            title = "Card holder verification",
            subtitle = "Pre-pay name check via CardHolderVerification",
            klass = CardHolderVerificationSampleActivity::class.java,
            section = MenuSection.Advanced,
        ),
        MenuItem(
            title = "Embedded in Views",
            subtitle = "XML layout hosting PaymentElement in a ComposeView",
            klass = EmbeddedViewsSampleActivity::class.java,
            section = MenuSection.Advanced,
        ),
        MenuItem(
            title = "Playground",
            subtitle = "Every PaymentConfig option — SDK development",
            klass = PlaygroundActivity::class.java,
            section = MenuSection.Internal,
        ),
    )

    val grouped = MenuSection.entries.associateWith { section ->
        items.filter { it.section == section }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            ExampleTopBar(
                title = "Examples",
                subtitle = "Copy-paste samples, merchant scenarios, and an internal playground.",
                showWordmark = true,
            )
        }
        MenuSection.entries.forEach { section ->
            val sectionItems = grouped[section].orEmpty()
            if (sectionItems.isEmpty()) return@forEach
            item {
                Text(
                    text = section.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
                        .alpha(if (section.muted) 0.7f else 1f),
                )
            }
            itemsIndexed(sectionItems) { index, item ->
                MenuRow(item)
                if (index < sectionItems.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MenuRow(item: MenuItem) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(context, item.klass))
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.imageRes?.let { res ->
            ExampleProductPhoto(
                imageRes = res,
                contentDescription = item.title,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
