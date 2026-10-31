package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.DraftInvoiceLine
import com.example.data.model.InvoiceCalculator
import com.example.data.model.InvoiceStatus
import com.example.data.model.Product
import com.example.ui.components.BengaliDatePickerDialog
import com.example.ui.components.VegIconAvatar
import com.example.ui.viewmodel.InvoiceFormState
import com.example.util.FormatUtils

@Composable
fun CreateInvoiceScreen(
    form: InvoiceFormState,
    settings: BusinessSettings,
    activeProducts: List<Product>,
    activeCustomers: List<Customer>,
    isBusy: Boolean,
    onUpdateHeader: (
        invoiceNumber: String?,
        selectedCustomer: Customer?,
        clearSavedCustomer: Boolean,
        customerName: String?,
        customerPhone: String?,
        deliveryAddress: String?,
        invoiceDateMillis: Long?,
        status: String?,
        notes: String?
    ) -> Unit,
    onUpdateFinancials: (
        commissionEnabled: Boolean?,
        commissionRateText: String?,
        additionalChargesText: String?,
        discountText: String?
    ) -> Unit,
    onAddLine: (productId: String, productName: String, quantity: Double, unit: String, rate: Double) -> Unit,
    onUpdateLine: (localLineId: String, productName: String, quantity: Double, unit: String, rate: Double) -> Unit,
    onRemoveLine: (localLineId: String) -> Unit,
    onQuickSaveCustomer: (Customer, (Customer) -> Unit) -> Unit,
    onSaveInvoice: (openDetailAfterSave: Boolean) -> Unit
) {
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }
    val totals = form.totals

    var showCustomerPicker by rememberSaveable { mutableStateOf(false) }
    var showQuickAddCustomer by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showProductLineModal by rememberSaveable { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<DraftInvoiceLine?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("create_invoice_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Invoice Business & Chalan Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (form.editingInvoice != null) "সংরক্ষিত চালান সম্পাদনা" else "নতুন পাইকারি সবজি চালান",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "তারিখ পরিবর্তন",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = FormatUtils.formatDate(form.invoiceDateMillis, settings.dateFormatPattern, useBn),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = form.invoiceNumber,
                        onValueChange = {
                            onUpdateHeader(it, null, false, null, null, null, null, null, null)
                        },
                        label = { Text("চালান নম্বর (স্বয়ংক্রিয়)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_number_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Customer Selector + Walk-in Support
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCustomerPicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("select_saved_customer_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("কাস্টমার নির্বাচন")
                        }
                        OutlinedButton(
                            onClick = { showQuickAddCustomer = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("quick_add_customer_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "নতুন কাস্টমার যোগ")
                        }
                    }

                    if (form.selectedCustomerId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "নিবন্ধিত কাস্টমার: ${form.customerName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                IconButton(
                                    onClick = {
                                        onUpdateHeader(null, null, true, "", "", "", null, null, null)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "নগদ ক্রেতায় পরিবর্তন", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = form.customerName,
                        onValueChange = {
                            onUpdateHeader(null, null, false, it, null, null, null, null, null)
                        },
                        label = { Text("নাম (খালি রাখলে 'নগদ ক্রেতা')") },
                        placeholder = { Text("নগদ ক্রেতা / পাইকারের নাম") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_customer_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.customerPhone,
                            onValueChange = {
                                onUpdateHeader(null, null, false, null, it, null, null, null, null)
                            },
                            label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = form.deliveryAddress,
                            onValueChange = {
                                onUpdateHeader(null, null, false, null, null, it, null, null, null)
                            },
                            label = { Text("ডেলিভারি ঠিকানা (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("চালান স্ট্যাটাস:", style = MaterialTheme.typography.labelLarge)
                        InvoiceStatus.entries.forEach { st ->
                            FilterChip(
                                selected = form.status == st.key,
                                onClick = {
                                    onUpdateHeader(null, null, false, null, null, null, null, st.key, null)
                                },
                                label = { Text(st.labelBn) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Product Entry Table & Clear "পণ্য যোগ করুন" Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "সবজি ও পণ্যের তালিকা",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "একই সবজি ভিন্ন দরে আলাদা লাইনে যোগ করা যাবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingLine = null
                                showProductLineModal = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_product_line_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পণ্য যোগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (form.lines.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBasket,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "এখনও কোনো পণ্য যোগ করা হয়নি। ‘পণ্য যোগ করুন’ বাটনে চাপুন।",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("পণ্য", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.3f))
                            Text("পরিমাণ", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
                            Text("দর", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
                            Text("মোট", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1.05f))
                            Spacer(modifier = Modifier.width(68.dp))
                        }
                    }
                }
            }
        }

        // Individual Product Lines
        itemsIndexed(form.lines, key = { _, line -> line.localLineId }) { index, line ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("invoice_line_card_$index"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(
                            text = "${FormatUtils.formatNumber((index + 1).toDouble(), useBn, 0)}. ${line.productName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = FormatUtils.formatQuantity(line.quantity, line.unit, useBn),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.95f)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(line.rate, currency, useBn),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.85f)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(line.lineTotal, currency, useBn),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.05f)
                    )
                    Row {
                        IconButton(
                            onClick = {
                                editingLine = line
                                showProductLineModal = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "লাইন এডিট করুন",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onRemoveLine(line.localLineId) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "লাইন মুছে ফেলুন",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Commission & Financial Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "হিসাব ও কমিশন সারসংক্ষেপ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Commission Toggle & Override
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "কমিশন হিসাব সক্রিয়",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "সূত্র: পণ্যের মোট মূল্য × কমিশন হার ÷ ১০০",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = form.commissionEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateFinancials(enabled, null, null, null)
                            },
                            modifier = Modifier.testTag("commission_toggle_switch")
                        )
                    }

                    if (form.commissionEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = form.commissionRateText,
                            onValueChange = { onUpdateFinancials(null, it, null, null) },
                            label = { Text("কমিশন শতকরা হার (%) — কাস্টম ওভাররাইড") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("commission_rate_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.additionalChargesText,
                            onValueChange = { onUpdateFinancials(null, null, it, null) },
                            label = { Text("অতিরিক্ত চার্জ ($currency)") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = form.discountText,
                            onValueChange = { onUpdateFinancials(null, null, null, it) },
                            label = { Text("ছাড় / Discount ($currency)") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = {
                            onUpdateHeader(null, null, false, null, null, null, null, null, it)
                        },
                        label = { Text("চালান নোট / মন্তব্য (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    SummaryRow("মোট পণ্যের আইটেম:", "${FormatUtils.formatNumber(totals.itemCount.toDouble(), useBn, 0)} টি")
                    SummaryRow("মোট পণ্যের ওজন / পরিমাণ:", FormatUtils.formatQuantity(totals.totalQuantity, "কেজি", useBn))
                    SummaryRow(
                        "পণ্যের মোট মূল্য (Subtotal):",
                        FormatUtils.formatCurrency(totals.productSubtotal, currency, useBn),
                        bold = true
                    )
                    if (totals.commissionEnabled) {
                        SummaryRow(
                            "কমিশন (${FormatUtils.formatNumber(totals.commissionRate, useBn)}%):",
                            "+ ${FormatUtils.formatCurrency(totals.commissionAmount, currency, useBn)}"
                        )
                    } else {
                        SummaryRow("কমিশন:", "নিষ্ক্রিয় (৳০)")
                    }
                    if (totals.additionalCharges > 0.0) {
                        SummaryRow(
                            "অতিরিক্ত চার্জ:",
                            "+ ${FormatUtils.formatCurrency(totals.additionalCharges, currency, useBn)}"
                        )
                    }
                    if (totals.discount > 0.0) {
                        SummaryRow(
                            "ছাড় (Discount):",
                            "− ${FormatUtils.formatCurrency(totals.discount, currency, useBn)}"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "সর্বমোট চালান মূল্য:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = FormatUtils.formatCurrency(totals.finalAmount, currency, useBn),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("final_invoice_amount_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onSaveInvoice(true) },
                        enabled = !isBusy && form.lines.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_invoice_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("সংরক্ষণ হচ্ছে...")
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (form.editingInvoice != null) "চালান আপডেট ও প্রিভিউ দেখুন" else "চালান সংরক্ষণ ও প্রিন্ট প্রিভিউ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Date Picker Modal
    if (showDatePicker) {
        BengaliDatePickerDialog(
            initialMillis = form.invoiceDateMillis,
            onDateSelected = { picked ->
                onUpdateHeader(null, null, false, null, null, null, picked, null, null)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Saved Customer Picker Dialog
    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = activeCustomers,
            onSelectCustomer = { cust ->
                onUpdateHeader(null, cust, false, null, null, null, null, null, null)
                showCustomerPicker = false
            },
            onSelectWalkIn = {
                onUpdateHeader(null, null, true, "নগদ ক্রেতা", "", "", null, null, null)
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    // Quick Add Customer Dialog
    if (showQuickAddCustomer) {
        QuickAddCustomerDialog(
            onSave = { newCust ->
                onQuickSaveCustomer(newCust) { savedCust ->
                    onUpdateHeader(null, savedCust, false, null, null, null, null, null, null)
                }
                showQuickAddCustomer = false
            },
            onDismiss = { showQuickAddCustomer = false }
        )
    }

    // Product Add / Edit Line Modal
    if (showProductLineModal) {
        ProductLineEditorDialog(
            existingLine = editingLine,
            catalogProducts = activeProducts,
            currencySymbol = currency,
            useBengaliNumerals = useBn,
            onConfirm = { prodId, prodName, qty, unit, rate ->
                val target = editingLine
                if (target == null) {
                    onAddLine(prodId, prodName, qty, unit, rate)
                } else {
                    onUpdateLine(target.localLineId, prodName, qty, unit, rate)
                }
                showProductLineModal = false
                editingLine = null
            },
            onDismiss = {
                showProductLineModal = false
                editingLine = null
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProductLineEditorDialog(
    existingLine: DraftInvoiceLine?,
    catalogProducts: List<Product>,
    currencySymbol: String,
    useBengaliNumerals: Boolean,
    onConfirm: (productId: String, productName: String, quantity: Double, unit: String, rate: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedProductId by rememberSaveable { mutableStateOf(existingLine?.productId ?: "") }
    var productName by rememberSaveable { mutableStateOf(existingLine?.productName ?: "") }
    var quantityText by rememberSaveable {
        mutableStateOf(
            existingLine?.let { FormatUtils.formatNumber(it.quantity, useBengaliNumerals = false) } ?: ""
        )
    }
    var rateText by rememberSaveable {
        mutableStateOf(
            existingLine?.let { FormatUtils.formatNumber(it.rate, useBengaliNumerals = false) } ?: ""
        )
    }
    var unitText by rememberSaveable { mutableStateOf(existingLine?.unit ?: "কেজি") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedQty = FormatUtils.parseFlexibleDouble(quantityText) ?: 0.0
    val parsedRate = FormatUtils.parseFlexibleDouble(rateText) ?: 0.0
    val previewTotal = InvoiceCalculator.calculateLineTotal(parsedQty, parsedRate)

    val filteredCatalog = remember(catalogProducts, searchQuery) {
        if (searchQuery.isBlank()) catalogProducts
        else catalogProducts.filter {
            it.nameBn.contains(searchQuery, ignoreCase = true) ||
                it.nameEn.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingLine != null) "পণ্য লাইন সম্পাদনা করুন" else "চালানে পণ্য যোগ করুন",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (existingLine == null && catalogProducts.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("তালিকা থেকে সবজি খুঁজুন") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCatalog, key = { it.id }) { prod ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedProductId == prod.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProductId = prod.id
                                        productName = prod.nameBn
                                        unitText = prod.defaultUnit.ifBlank { "কেজি" }
                                        rateText = FormatUtils.formatNumber(prod.defaultRate, useBengaliNumerals = false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VegIconAvatar(iconKey = prod.iconKey, size = 32.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = prod.nameBn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${FormatUtils.formatCurrency(prod.defaultRate, currencySymbol, useBengaliNumerals)}/${prod.defaultUnit}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("পণ্যের নাম (বাংলায়)") },
                    placeholder = { Text("যেমন: আলু, পেঁয়াজ, বেগুন") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_product_name_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("পরিমাণ (কেজি)") },
                        placeholder = { Text("50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("modal_quantity_input")
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("দর ($currencySymbol/কেজি)") },
                        placeholder = { Text("30") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("modal_rate_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("লাইন মোট (পরিমাণ × দর):", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = FormatUtils.formatCurrency(previewTotal, currencySymbol, useBengaliNumerals),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = FormatUtils.parseFlexibleDouble(quantityText)
                    val rate = FormatUtils.parseFlexibleDouble(rateText)
                    when {
                        productName.isBlank() -> validationError = "পণ্যের নাম লিখুন"
                        qty == null || qty <= 0.0 -> validationError = "সঠিক পরিমাণ (> ০) লিখুন"
                        rate == null || rate < 0.0 -> validationError = "সঠিক দর (≥ ০) লিখুন"
                        else -> onConfirm(selectedProductId, productName, qty, unitText, rate)
                    }
                },
                modifier = Modifier.testTag("modal_confirm_line_button")
            ) {
                Text(if (existingLine != null) "আপডেট করুন" else "চালানে যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
private fun CustomerPickerDialog(
    customers: List<Customer>,
    onSelectCustomer: (Customer) -> Unit,
    onSelectWalkIn: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("কাস্টমার নির্বাচন করুন", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSelectWalkIn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("নগদ ক্রেতা (Walk-in Customer — অ্যাকাউন্ট ছাড়া)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("নাম বা মোবাইল দিয়ে খুঁজুন") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { cust ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCustomer(cust) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("নাম: ${cust.name}", fontWeight = FontWeight.SemiBold)
                                if (cust.phone.isNotBlank()) {
                                    Text("মোবাইল: ${cust.phone}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@Composable
private fun QuickAddCustomerDialog(
    onSave: (Customer) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("দ্রুত নতুন কাস্টমার যোগ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ঠিকানা (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) {
                    err = "কাস্টমারের নাম লিখুন"
                } else {
                    onSave(Customer(name = name.trim(), phone = phone.trim(), address = address.trim()))
                }
            }) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
