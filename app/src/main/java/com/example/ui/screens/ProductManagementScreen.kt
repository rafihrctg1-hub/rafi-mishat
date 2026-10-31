package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.data.model.Product
import com.example.data.model.VegetableIcon
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.VegIconAvatar
import com.example.util.FormatUtils

private enum class ProductStatusFilter(val labelBn: String) {
    ALL("সব"),
    ACTIVE("সক্রিয়"),
    INACTIVE("নিষ্ক্রিয়")
}

@Composable
fun ProductManagementScreen(
    products: List<Product>,
    settings: BusinessSettings,
    isBusy: Boolean,
    onSaveProduct: (Product, () -> Unit) -> Unit,
    onToggleActive: (Product) -> Unit,
    onSeedStarterProducts: () -> Unit
) {
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(ProductStatusFilter.ALL) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = remember(products, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase()
        products.filter { p ->
            val matchesStatus = when (statusFilter) {
                ProductStatusFilter.ALL -> true
                ProductStatusFilter.ACTIVE -> p.isActive
                ProductStatusFilter.INACTIVE -> !p.isActive
            }
            val matchesSearch = q.isEmpty() ||
                p.nameBn.lowercase().contains(q) ||
                p.nameEn.lowercase().contains(q) ||
                p.notes.lowercase().contains(q)
            matchesStatus && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("product_management_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "সবজি ও পণ্য তালিকা",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "মোট ${FormatUtils.formatNumber(products.size.toDouble(), useBn, 0)}টি পণ্য নিবন্ধিত",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingProduct = null
                                showEditor = true
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_product_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("নতুন পণ্য")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_search_input"),
                        placeholder = { Text("সবজির নাম (বাংলা বা English) লিখে খুঁজুন...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "খুঁজুন") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProductStatusFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = statusFilter == filter,
                                onClick = { statusFilter = filter },
                                label = { Text(filter.labelBn) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "টিপস: একই সবজি (যেমন আলু) ভিন্ন দরে একাধিকবার পণ্য তালিকায় বা চালানে আলাদা লাইনে যোগ করা যাবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (products.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyStateCard(
                            icon = Icons.Default.Inventory2,
                            title = "এখনও কোনো সবজি যোগ করা হয়নি",
                            subtitle = "নিজে নতুন পণ্য যোগ করুন অথবা এক ক্লিকে বাংলাদেশের প্রচলিত পাইকারি সবজি তালিকা যুক্ত করুন।"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onSeedStarterProducts,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("seed_starter_products_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("প্রাথমিক সবজি তালিকা যোগ করুন")
                        }
                    }
                }
            }
        } else if (filteredProducts.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Search,
                    title = "কোনো পণ্য পাওয়া যায়নি",
                    subtitle = "অন্য নাম দিয়ে খুঁজুন বা ফিল্টার পরিবর্তন করুন।"
                )
            }
        } else {
            items(filteredProducts, key = { it.id }) { product ->
                ProductListItemCard(
                    product = product,
                    currency = currency,
                    useBn = useBn,
                    onEdit = {
                        editingProduct = product
                        showEditor = true
                    },
                    onToggleActive = { onToggleActive(product) }
                )
            }
        }
    }

    if (showEditor) {
        ProductEditorDialog(
            initialProduct = editingProduct,
            isBusy = isBusy,
            onDismiss = { showEditor = false },
            onSave = { draft ->
                onSaveProduct(draft) {
                    showEditor = false
                }
            }
        )
    }
}

@Composable
private fun ProductListItemCard(
    product: Product,
    currency: String,
    useBn: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (product.isActive) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VegIconAvatar(iconKey = product.iconKey, size = 50.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.nameBn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (product.nameEn.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${product.nameEn})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val rateLabel = if (product.defaultRate > 0.0) {
                    "ডিফল্ট দর: ${FormatUtils.formatCurrency(product.defaultRate, currency, useBn)} / ${product.defaultUnit}"
                } else {
                    "একক: ${product.defaultUnit} (চালানে দর নির্ধারণযোগ্য)"
                }
                Text(
                    text = rateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (product.notes.isNotBlank()) {
                    Text(
                        text = product.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = if (product.isActive) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (product.isActive) "সক্রিয়" else "নিষ্ক্রিয়",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (product.isActive) Color(0xFF14532D) else Color(0xFF4B5563),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "পণ্য সম্পাদনা",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = product.isActive,
                    onCheckedChange = { onToggleActive() }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductEditorDialog(
    initialProduct: Product?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var nameBn by rememberSaveable { mutableStateOf(initialProduct?.nameBn ?: "") }
    var nameEn by rememberSaveable { mutableStateOf(initialProduct?.nameEn ?: "") }
    var iconKey by rememberSaveable { mutableStateOf(initialProduct?.iconKey ?: VegetableIcon.POTATO.key) }
    var defaultUnit by rememberSaveable { mutableStateOf(initialProduct?.defaultUnit ?: "কেজি") }
    var defaultRateText by rememberSaveable {
        mutableStateOf(
            if (initialProduct != null && initialProduct.defaultRate > 0.0) {
                FormatUtils.formatNumber(initialProduct.defaultRate, useBengaliNumerals = false)
            } else {
                ""
            }
        )
    }
    var notes by rememberSaveable { mutableStateOf(initialProduct?.notes ?: "") }
    var isActive by rememberSaveable { mutableStateOf(initialProduct?.isActive ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val commonUnits = listOf("কেজি", "বস্তা", "মণ", "পিস")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProduct == null) "নতুন সবজি / পণ্য যোগ করুন" else "পণ্য সম্পাদনা করুন",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = nameBn,
                    onValueChange = {
                        nameBn = it
                        validationError = null
                    },
                    label = { Text("পণ্যের নাম (বাংলা) *") },
                    placeholder = { Text("যেমন: আলু, পেঁয়াজ, বেগুন") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_bn_input")
                )

                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text("ইংরেজি নাম (ঐচ্ছিক)") },
                    placeholder = { Text("e.g. Potato, Onion") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "সবজির আইকন নির্বাচন করুন (সাদা ব্যাকগ্রাউন্ড ও সবুজ বর্ডার):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VegetableIcon.entries.forEach { veg ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(56.dp)
                        ) {
                            VegIconAvatar(
                                iconKey = veg.key,
                                size = 48.dp,
                                selected = iconKey == veg.key,
                                onClick = { iconKey = veg.key }
                            )
                            Text(
                                text = veg.labelBn,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = defaultRateText,
                        onValueChange = { defaultRateText = it },
                        label = { Text("ডিফল্ট দর (৳)") },
                        placeholder = { Text("যেমন: ৩০ বা 32.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_rate_input")
                    )

                    OutlinedTextField(
                        value = defaultUnit,
                        onValueChange = { defaultUnit = it },
                        label = { Text("একক") },
                        singleLine = true,
                        modifier = Modifier.weight(0.85f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonUnits.forEach { u ->
                        FilterChip(
                            selected = defaultUnit == u,
                            onClick = { defaultUnit = u },
                            label = { Text(u) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট / গ্রেড (ঐচ্ছিক)") },
                    placeholder = { Text("যেমন: দেশি লাল আলু / গ্রেড-এ") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("সক্রিয় পণ্য (চালানে দেখাবে)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = nameBn.trim()
                    if (trimmedName.isBlank()) {
                        validationError = "অনুগ্রহ করে পণ্যের বাংলা নাম লিখুন"
                        return@Button
                    }
                    val parsedRate = if (defaultRateText.isBlank()) {
                        0.0
                    } else {
                        FormatUtils.parseFlexibleDouble(defaultRateText)
                    }
                    if (parsedRate == null || parsedRate < 0.0) {
                        validationError = "সঠিক ডিফল্ট দর প্রদান করুন (০ বা তার বেশি)"
                        return@Button
                    }

                    val productToSave = (initialProduct ?: Product()).copy(
                        nameBn = trimmedName,
                        nameEn = nameEn.trim(),
                        iconKey = iconKey,
                        defaultUnit = defaultUnit.trim().ifBlank { "কেজি" },
                        defaultRate = parsedRate,
                        notes = notes.trim(),
                        isActive = isActive
                    )
                    onSave(productToSave)
                },
                enabled = !isBusy,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_product_confirm_button")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("বাতিল")
            }
        }
    )
}
