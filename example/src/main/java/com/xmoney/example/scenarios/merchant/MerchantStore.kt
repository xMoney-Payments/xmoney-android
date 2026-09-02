package com.xmoney.example.scenarios.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmoney.example.BuildConfig
import com.xmoney.example.backend.DemoCheckoutBackend
import com.xmoney.example.defaultPaymentConfig
import com.xmoney.example.formatMoney
import com.xmoney.example.orderConsumed
import com.xmoney.example.theme.ExampleRadii
import com.xmoney.example.theme.ExampleThemeController
import com.xmoney.example.theme.LocalBrandAccent
import com.xmoney.example.theme.LocalBrandAccentText
import com.xmoney.example.theme.LocalBrandOnAccent
import com.xmoney.example.ui.ExampleAddChip
import com.xmoney.example.ui.ExampleButton
import com.xmoney.example.ui.ExampleButtonVariant
import com.xmoney.example.ui.ExampleCard
import com.xmoney.example.ui.ExampleCheckoutSkeleton
import com.xmoney.example.ui.ExampleProductPhoto
import com.xmoney.example.ui.ExampleResultPanel
import com.xmoney.example.ui.ExampleStatusChip
import com.xmoney.example.ui.ExampleStatusKind
import com.xmoney.example.ui.ExampleTopBar
import com.xmoney.example.ui.MerchantReadyGate
import com.xmoney.paymentelement.EmbeddedEvent
import com.xmoney.paymentelement.PaymentElement
import com.xmoney.paymentelement.rememberEmbeddedPayment
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.PaymentResult
import com.xmoney.paymentsheet.PaymentSheetEvent
import com.xmoney.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface MerchantRoute {
    data object Catalog : MerchantRoute
    data object Cart : MerchantRoute
    data class Checkout(val lines: List<MerchantLine>) : MerchantRoute
    data class Receipt(val lines: List<MerchantLine>, val result: PaymentResult) : MerchantRoute
}

@Composable
internal fun MerchantStore(
    brand: MerchantBrand,
    onLeave: () -> Unit,
    viewModel: MerchantStoreViewModel = viewModel(),
) {
    val dark = ExampleThemeController.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalBrandAccent provides brand.accent,
        LocalBrandOnAccent provides brand.onAccent,
        LocalBrandAccentText provides if (dark) brand.accent else brand.accentText,
    ) {
        var route by remember { mutableStateOf<MerchantRoute>(MerchantRoute.Catalog) }
        val lines = remember(viewModel.quantities, brand) {
            viewModel.quantities.toMerchantLines(brand.products)
        }

        when (val current = route) {
            MerchantRoute.Catalog -> CatalogScreen(
                brand = brand,
                lines = lines,
                quantityOf = { viewModel.quantities[it] ?: 0 },
                onBack = onLeave,
                onAdd = { id -> viewModel.setQty(id, (viewModel.quantities[id] ?: 0) + 1) },
                onOpenCart = { route = MerchantRoute.Cart },
            )
            MerchantRoute.Cart -> CartScreen(
                brand = brand,
                lines = lines,
                onBack = { route = MerchantRoute.Catalog },
                onQty = viewModel::setQty,
                onCheckout = { route = MerchantRoute.Checkout(lines) },
            )
            is MerchantRoute.Checkout -> when (brand.paySurface) {
                MerchantPaySurface.PaymentSheet -> SheetCheckoutScreen(
                    brand = brand,
                    lines = current.lines,
                    onBack = { route = MerchantRoute.Cart },
                    onFinished = { result ->
                        route = MerchantRoute.Receipt(current.lines, result)
                    },
                )
                MerchantPaySurface.Embedded -> EmbeddedCheckoutScreen(
                    brand = brand,
                    lines = lines,
                    onQty = viewModel::setQty,
                    onBack = { route = MerchantRoute.Cart },
                    onFinished = { result ->
                        route = MerchantRoute.Receipt(lines, result)
                    },
                )
            }
            is MerchantRoute.Receipt -> ReceiptScreen(
                brand = brand,
                lines = current.lines,
                result = current.result,
                onDone = {
                    viewModel.clear()
                    route = MerchantRoute.Catalog
                },
                onRetry = { route = MerchantRoute.Checkout(current.lines) },
                onBackToCart = { route = MerchantRoute.Cart },
            )
        }
    }
}

@Composable
private fun CatalogScreen(
    brand: MerchantBrand,
    lines: List<MerchantLine>,
    quantityOf: (String) -> Int,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onOpenCart: () -> Unit,
) {
    val currency = BuildConfig.CURRENCY
    val count = lines.itemCount
    val grouped = remember(brand.products) { brand.products.groupBy { it.category } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    ExampleButton(
                        label = "Cart · $count ${if (count == 1) "item" else "items"} · ${formatMoney(lines.subtotalMinor, currency)}",
                        onClick = onOpenCart,
                    )
                }
            }
        },
    ) { padding ->
        when (brand.catalogStyle) {
            MerchantCatalogStyle.Grid -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    StoreTopBar(brand, count, onBack, onOpenCart)
                }
                items(brand.products, key = { it.id }) { product ->
                    GridProductCard(
                        product = product,
                        quantity = quantityOf(product.id),
                        currency = currency,
                        onAdd = { onAdd(product.id) },
                    )
                }
            }
            MerchantCatalogStyle.Menu, MerchantCatalogStyle.Plans -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StoreTopBar(brand, count, onBack, onOpenCart) }
                grouped.forEach { (category, products) ->
                    item {
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(products, key = { it.id }) { product ->
                        when (brand.catalogStyle) {
                            MerchantCatalogStyle.Menu -> MenuRow(
                                product = product,
                                quantity = quantityOf(product.id),
                                currency = currency,
                                onAdd = { onAdd(product.id) },
                            )
                            else -> PlanCard(
                                product = product,
                                quantity = quantityOf(product.id),
                                currency = currency,
                                onAdd = { onAdd(product.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreTopBar(
    brand: MerchantBrand,
    count: Int,
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
) {
    ExampleTopBar(
        title = brand.name,
        subtitle = brand.tagline,
        onBack = onBack,
        showWordmark = true,
        showThemeToggle = false,
        actions = { CartBadge(count = count, onClick = onOpenCart) },
    )
}

@Composable
private fun GridProductCard(
    product: MerchantProduct,
    quantity: Int,
    currency: String,
    onAdd: () -> Unit,
) {
    val accentText = LocalBrandAccentText.current
    val shape = RoundedCornerShape(ExampleRadii.card)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        ExampleProductPhoto(
            imageRes = product.imageRes,
            contentDescription = product.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = product.category.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMoney(product.priceMinor, currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentText,
                )
                ExampleAddChip(quantity = quantity, onClick = onAdd)
            }
        }
    }
}

@Composable
private fun MenuRow(
    product: MerchantProduct,
    quantity: Int,
    currency: String,
    onAdd: () -> Unit,
) {
    val accentText = LocalBrandAccentText.current
    val shape = RoundedCornerShape(ExampleRadii.inner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExampleProductPhoto(
            imageRes = product.imageRes,
            contentDescription = product.name,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = product.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMoney(product.priceMinor, currency),
                style = MaterialTheme.typography.titleMedium,
                color = accentText,
            )
        }
        ExampleAddChip(quantity = quantity, onClick = onAdd)
    }
}

@Composable
private fun PlanCard(
    product: MerchantProduct,
    quantity: Int,
    currency: String,
    onAdd: () -> Unit,
) {
    val accentText = LocalBrandAccentText.current
    val shape = RoundedCornerShape(ExampleRadii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        ExampleProductPhoto(
            imageRes = product.imageRes,
            contentDescription = product.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = product.category.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(product.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = product.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMoney(product.priceMinor, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentText,
                )
                ExampleAddChip(quantity = quantity, onClick = onAdd)
            }
        }
    }
}

@Composable
private fun CartBadge(count: Int, onClick: () -> Unit) {
    val accent = LocalBrandAccent.current
    val onAccent = LocalBrandOnAccent.current
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ShoppingBag,
            contentDescription = "Cart",
            tint = MaterialTheme.colorScheme.onBackground,
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = onAccent,
                )
            }
        }
    }
}

@Composable
private fun CartScreen(
    brand: MerchantBrand,
    lines: List<MerchantLine>,
    onBack: () -> Unit,
    onQty: (String, Int) -> Unit,
    onCheckout: () -> Unit,
) {
    val currency = BuildConfig.CURRENCY
    val empty = lines.isEmpty()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!empty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TotalsBlock(lines = lines, currency = currency)
                    ExampleButton(
                        label = "Checkout · ${formatMoney(lines.subtotalMinor, currency)}",
                        onClick = onCheckout,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExampleTopBar(
                title = "Cart",
                subtitle = if (empty) "No items yet." else "${lines.itemCount} items",
                onBack = onBack,
                showThemeToggle = false,
            )
            if (empty) {
                ExampleCard {
                    Text("Your bag is empty", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = brand.emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ExampleButton(
                        label = "Continue shopping",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = onBack,
                    )
                }
            } else {
                ExampleCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Column {
                        lines.forEachIndexed { index, line ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                            CartLineRow(line = line, currency = currency, onQty = { onQty(line.product.id, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(
    line: MerchantLine,
    currency: String,
    onQty: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExampleProductPhoto(
            imageRes = line.product.imageRes,
            contentDescription = line.product.name,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(ExampleRadii.inner)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = formatMoney(line.product.priceMinor, currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QtyStepper(quantity = line.quantity, onQty = onQty)
        }
        Text(
            text = formatMoney(line.lineTotalMinor, currency),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun QtyStepper(quantity: Int, onQty: (Int) -> Unit) {
    val shape = RoundedCornerShape(ExampleRadii.pill)
    Row(
        modifier = Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onQty(quantity - 1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { onQty(quantity + 1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TotalsBlock(lines: List<MerchantLine>, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(lines.subtotalMinor, currency), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(lines.subtotalMinor, currency), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OrderSummaryCard(
    lines: List<MerchantLine>,
    currency: String,
    onQty: ((String, Int) -> Unit)? = null,
) {
    ExampleCard {
        Text(
            text = "ORDER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lines.forEach { line ->
            if (onQty != null) {
                CartLineRow(line = line, currency = currency, onQty = { onQty(line.product.id, it) })
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${line.product.name} × ${line.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMoney(line.lineTotalMinor, currency),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TotalsBlock(lines = lines, currency = currency)
    }
}

@Composable
private fun SheetCheckoutScreen(
    brand: MerchantBrand,
    lines: List<MerchantLine>,
    onBack: () -> Unit,
    onFinished: (PaymentResult) -> Unit,
) {
    val currency = BuildConfig.CURRENCY
    val total = lines.subtotalMinor
    val description = "${brand.name} · ${lines.itemCount} ${if (lines.itemCount == 1) "item" else "items"}"
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var heldIntent by remember { mutableStateOf<PaymentIntent?>(null) }
    var didProcess by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val paymentSheet = rememberPaymentSheet(
        configuration = defaultPaymentConfig(appearance = brand.appearance),
        onResult = { result ->
            loading = false
            if (orderConsumed(result, didProcess)) {
                heldIntent = null
                onFinished(result)
            }
        },
    )

    LaunchedEffect(retryKey) {
        if (heldIntent != null) return@LaunchedEffect
        error = null
        try {
            heldIntent = DemoCheckoutBackend.createPaymentIntent(
                amountMinor = total,
                description = description,
            )
            revealed = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not create order"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    revealed -> {
                        error?.let { ExampleStatusChip(it, ExampleStatusKind.Error) }
                        ExampleButton(
                            label = "Pay ${formatMoney(total, currency)}",
                            loading = loading,
                            onClick = {
                                val intent = heldIntent ?: return@ExampleButton
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        didProcess = false
                                        paymentSheet.present(intent) { event ->
                                            when (event) {
                                                PaymentSheetEvent.Ready -> loading = false
                                                is PaymentSheetEvent.Processing -> {
                                                    if (event.isProcessing) didProcess = true
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not open checkout"
                                        loading = false
                                    }
                                }
                            },
                        )
                    }
                    error != null -> {
                        ExampleStatusChip(error!!, ExampleStatusKind.Error)
                        ExampleButton(
                            label = "Try again",
                            variant = ExampleButtonVariant.Secondary,
                            onClick = { retryKey += 1 },
                        )
                    }
                    else -> ExampleCheckoutSkeleton(showOrder = false, showPayButton = true)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExampleTopBar(
                title = "Checkout",
                subtitle = "Pay with Payment Sheet.",
                onBack = onBack,
                showThemeToggle = false,
            )
            when {
                revealed -> OrderSummaryCard(lines = lines, currency = currency)
                error == null -> ExampleCheckoutSkeleton(showOrder = true)
            }
        }
    }
}

@Composable
private fun EmbeddedCheckoutScreen(
    brand: MerchantBrand,
    lines: List<MerchantLine>,
    onQty: (String, Int) -> Unit,
    onBack: () -> Unit,
    onFinished: (PaymentResult) -> Unit,
) {
    val currency = BuildConfig.CURRENCY
    val total = lines.subtotalMinor
    val description = "${brand.name} · ${lines.itemCount} ${if (lines.itemCount == 1) "item" else "items"}"
    var intent by remember { mutableStateOf<PaymentIntent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var pendingResult by remember { mutableStateOf<PaymentResult?>(null) }
    val embedded = rememberEmbeddedPayment(
        configuration = defaultPaymentConfig(appearance = brand.appearance),
        onResult = { pendingResult = it },
    )

    LaunchedEffect(lines.isEmpty()) {
        if (lines.isEmpty()) onBack()
    }

    LaunchedEffect(pendingResult, embedded.isOrderConsumed) {
        val result = pendingResult ?: return@LaunchedEffect
        if (result is PaymentResult.Canceled && !embedded.isOrderConsumed) {
            pendingResult = null
            return@LaunchedEffect
        }
        pendingResult = null
        onFinished(result)
    }

    LaunchedEffect(embedded, total, description, retryKey) {
        if (lines.isEmpty()) return@LaunchedEffect
        if (intent != null) delay(300)
        error = null
        try {
            // PaymentElement calls updateOrder when [intent] changes. Keep it
            // mounted — do not clear intent or flip Ready.
            intent = DemoCheckoutBackend.createPaymentIntent(
                amountMinor = total,
                description = description,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not create order"
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ExampleTopBar(
                    title = "Checkout",
                    subtitle = "Pay with Embedded Payment Element.",
                    onBack = onBack,
                    showThemeToggle = false,
                )
            }
            if (ready) {
                item { OrderSummaryCard(lines = lines, currency = currency, onQty = onQty) }
            } else if (error == null) {
                item { ExampleCheckoutSkeleton(showOrder = true) }
            }
            if (intent != null) {
                item {
                    MerchantReadyGate(
                        ready = ready,
                        message = "Preparing checkout…",
                        placeholder = { ExampleCheckoutSkeleton(showOrder = false, showForm = true) },
                    ) {
                        PaymentElement(
                            controller = embedded,
                            intent = intent!!,
                            modifier = Modifier.fillMaxWidth(),
                            onEvent = { event ->
                                if (event is EmbeddedEvent.Ready) ready = true
                            },
                        )
                    }
                }
            } else if (error == null) {
                item { ExampleCheckoutSkeleton(showOrder = false, showForm = true) }
            }
            error?.let {
                item {
                    ExampleStatusChip(it, ExampleStatusKind.Error)
                    ExampleButton(
                        label = "Try again",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = { retryKey += 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptScreen(
    brand: MerchantBrand,
    lines: List<MerchantLine>,
    result: PaymentResult,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onBackToCart: () -> Unit,
) {
    val currency = BuildConfig.CURRENCY
    val success = result is PaymentResult.Complete
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (success) {
                    ExampleButton(label = "Back to ${brand.name}", onClick = onDone)
                } else {
                    ExampleButton(label = "Try again", onClick = onRetry)
                    ExampleButton(
                        label = "Back to cart",
                        variant = ExampleButtonVariant.Secondary,
                        onClick = onBackToCart,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExampleTopBar(
                title = if (success) "Receipt" else "Payment",
                showWordmark = true,
                showThemeToggle = false,
            )
            ExampleResultPanel(
                result = result,
                fallbackAmount = formatMoney(lines.subtotalMinor, currency),
                successTitle = "Order confirmed",
                failureTitle = "Payment didn’t go through",
            )
            if (lines.isNotEmpty()) {
                ExampleCard {
                    Text(
                        text = "ITEMS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lines.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${line.product.name} × ${line.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                formatMoney(line.lineTotalMinor, currency),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
