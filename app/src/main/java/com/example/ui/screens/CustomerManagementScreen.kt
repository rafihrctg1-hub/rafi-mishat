package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.ui.components.BengaliDatePickerDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InvoiceStatusBadge
import com.example.ui.components.SummaryMetricCard
import com.example.util.FormatUtils
import com.example.util.PdfAndPrintManager
import java.util.Calendar

private enum class CustomerStatusFilter(val labelBn: String) {
    ALL("সব"),
    ACTIVE("সক্রিয়"),
    INACTIVE("নিষ্ক্রিয়")
}

private enum class CustomerHistoryRange(val labelBn: String) {
    ALL("সব তারিখ"),
    TODAY("আজ"),
    THIS_MONTH("এই মাস"),
    CUSTOM("কাস্টম তারিখ")
}

@Composable
fun CustomerListScreen(
    customers: List<Customer>,
    invoices: List<Invoice>,
    settings: BusinessSettings,
    isBusy: Boolean,
    onOpenCustomerDetail: (String) -> Unit,
    onStartInvoiceForCustomer: (Customer) -> Unit,
    onSaveCustomer: (Customer, () -> Unit) -> Unit,
    onToggleActive: (Customer) -> Unit
) {
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(CustomerStatusFilter.ALL) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }

    val invoiceStatsByCustomer = remember(invoices) {
        invoices
            .filter { it.customerId.isNotBlank() }
            .groupBy { it.customerId }
            .mapValues { (_, list) ->
                list.size to list.sumOf { it.finalAmount }
            }
    }

    val filteredCustomers = remember(customers, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase()
        customers.filter { c ->
            val matchesStatus = when (statusFilter) {
                CustomerStatusFilter.ALL -> true
                CustomerStatusFilter.ACTIVE -> c.isActive
                CustomerStatusFilter.INACTIVE -> !c.isActive
            }
            val matchesSearch = q.isEmpty() ||
                c.name.lowercase().contains(q) ||
                c.phone.lowercase().contains(q) ||
                c.address.lowercase().contains(q)
            matchesStatus && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("customer_list_screen"),
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
                                text = "কাস্টমার তালিকা",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "মোট ${FormatUtils.formatNumber(customers.size.toDouble(), useBn, 0)} জন কাস্টমার",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingCustomer = null
                                showEditor = true
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_customer_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("নতুন কাস্টমার")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_search_input"),
                        placeholder = { Text("নাম বা মোবাইল নম্বর দিয়ে খুঁজুন...") },
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
                        CustomerStatusFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = statusFilter == filter,
                                onClick = { statusFilter = filter },
                                label = { Text(filter.labelBn) }
                            )
                        }
                    }
                }
            }
        }

        if (filteredCustomers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.People,
                    title = if (customers.isEmpty()) "কোনো কাস্টমার সংরক্ষিত নেই" else "কোনো কাস্টমার পাওয়া যায়নি",
                    subtitle = "স্থায়ী কাস্টমারদের নাম ও মোবাইল সংরক্ষণ করলে এক ক্লিকে চালান তৈরি ও পূর্ববর্তী হিসাব দেখা যাবে। নগদ ক্রেতার জন্য কাস্টমার খোলা বাধ্যতামূলক নয়।",
                    actionLabel = if (customers.isEmpty()) "নতুন কাস্টমার যোগ করুন" else null,
                    onActionClick = if (customers.isEmpty()) {
                        {
                            editingCustomer = null
                            showEditor = true
                        }
                    } else null
                )
            }
        } else {
            items(filteredCustomers, key = { it.id }) { customer ->
                val stats = invoiceStatsByCustomer[customer.id] ?: (0 to 0.0)
                CustomerCard(
                    customer = customer,
                    invoiceCount = stats.first,
                    totalPurchased = stats.second,
                    settings = settings,
                    currency = currency,
                    useBn = useBn,
                    onClick = { onOpenCustomerDetail(customer.id) },
                    onEdit = {
                        editingCustomer = customer
                        showEditor = true
                    },
                    onStartInvoice = { onStartInvoiceForCustomer(customer) },
                    onToggleActive = { onToggleActive(customer) }
                )
            }
        }
    }

    if (showEditor) {
        CustomerEditorDialog(
            initialCustomer = editingCustomer,
            isBusy = isBusy,
            onDismiss = { showEditor = false },
            onSave = { draft ->
                onSaveCustomer(draft) {
                    showEditor = false
                }
            }
        )
    }
}

@Composable
private fun CustomerCard(
    customer: Customer,
    invoiceCount: Int,
    totalPurchased: Double,
    settings: BusinessSettings,
    currency: String,
    useBn: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onStartInvoice: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (customer.isActive) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.take(1).ifBlank { "ক" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "নাম: ${customer.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (customer.phone.isNotBlank()) {
                        Text(
                            text = "মোবাইল: ${customer.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customer.address.isNotBlank()) {
                        Text(
                            text = "ঠিকানা: ${customer.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "কাস্টমার সম্পাদনা",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = customer.isActive,
                    onCheckedChange = { onToggleActive() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "মোট চালান: ${FormatUtils.formatNumber(invoiceCount.toDouble(), useBn, 0)}টি  •  মোট বিল: ${FormatUtils.formatCurrency(totalPurchased, currency, useBn)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    val createdMillis = customer.createdAt?.toDate()?.time ?: 0L
                    if (createdMillis > 0L) {
                        Text(
                            text = "যোগ করার তারিখ: ${FormatUtils.formatDate(createdMillis, settings.dateFormatPattern, useBn)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onStartInvoice,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("চালান")
                }
            }
        }
    }
}

// -------------------- CUSTOMER DETAIL & INVOICE HISTORY SCREEN --------------------

@Composable
fun CustomerDetailScreen(
    customer: Customer?,
    allInvoices: List<Invoice>,
    allInvoiceItems: List<InvoiceItem>,
    settings: BusinessSettings,
    isBusy: Boolean,
    onStartInvoiceForCustomer: (Customer) -> Unit,
    onOpenInvoiceDetail: (String) -> Unit,
    onSaveCustomer: (Customer, () -> Unit) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var dateRange by rememberSaveable { mutableStateOf(CustomerHistoryRange.ALL) }
    var customStartMillis by rememberSaveable {
        mutableLongStateOf(FormatUtils.startOfDayMillis(System.currentTimeMillis() - 30L * 86400000L))
    }
    var customEndMillis by rememberSaveable {
        mutableLongStateOf(FormatUtils.endOfDayMillis(System.currentTimeMillis()))
    }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    if (customer == null) {
        Column(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.People,
                title = "কাস্টমার পাওয়া যায়নি",
                subtitle = "তথ্য লোড হচ্ছে বা কাস্টমারটি মুছে ফেলা হয়েছে।"
            )
        }
        return
    }

    val customerInvoices = remember(allInvoices, customer.id, customer.name, dateRange, customStartMillis, customEndMillis) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val monthStart = FormatUtils.startOfMonthMillis(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        val monthEnd = FormatUtils.endOfMonthMillis(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        val todayStart = FormatUtils.startOfDayMillis(now)
        val todayEnd = FormatUtils.endOfDayMillis(now)

        allInvoices.filter { inv ->
            val belongsToCustomer = inv.customerId == customer.id ||
                (inv.customerId.isBlank() && inv.customerName.equals(customer.name, ignoreCase = true))
            val inRange = when (dateRange) {
                CustomerHistoryRange.ALL -> true
                CustomerHistoryRange.TODAY -> inv.invoiceDateMillis in todayStart..todayEnd
                CustomerHistoryRange.THIS_MONTH -> inv.invoiceDateMillis in monthStart..monthEnd
                CustomerHistoryRange.CUSTOM -> inv.invoiceDateMillis in customStartMillis..customEndMillis
            }
            belongsToCustomer && inRange
        }
    }

    val totalSubtotal = remember(customerInvoices) { customerInvoices.sumOf { it.productSubtotal } }
    val totalFinal = remember(customerInvoices) { customerInvoices.sumOf { it.finalAmount } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("customer_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "নাম: ${customer.name}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (customer.phone.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(customer.phone, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (customer.address.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(customer.address, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (customer.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "নোট: ${customer.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সম্পাদনা")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onStartInvoiceForCustomer(customer) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("customer_new_invoice_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("এই কাস্টমারের নতুন চালান তৈরি করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "চালান সংখ্যা",
                    value = FormatUtils.formatNumber(customerInvoices.size.toDouble(), useBn, 0),
                    subtitle = "পণ্য মূল: ${FormatUtils.formatCurrency(totalSubtotal, currency, useBn)}",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "মোট চালান বিল",
                    value = FormatUtils.formatCurrency(totalFinal, currency, useBn),
                    subtitle = "কমিশন ও চার্জসহ",
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column {
                Text(
                    text = "পূর্ববর্তী চালান ইতিহাস (তারিখ ফিল্টার):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomerHistoryRange.entries.forEach { r ->
                        FilterChip(
                            selected = dateRange == r,
                            onClick = { dateRange = r },
                            label = { Text(r.labelBn) }
                        )
                    }
                }
                if (dateRange == CustomerHistoryRange.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pickingStart = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("শুরু: ${FormatUtils.formatDate(customStartMillis, settings.dateFormatPattern, useBn)}")
                        }
                        OutlinedButton(
                            onClick = { pickingEnd = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("শেষ: ${FormatUtils.formatDate(customEndMillis, settings.dateFormatPattern, useBn)}")
                        }
                    }
                }
            }
        }

        if (customerInvoices.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "কোনো চালান পাওয়া যায়নি",
                    subtitle = "নির্বাচিত সময়ে এই কাস্টমারের কোনো চালান নেই।"
                )
            }
        } else {
            items(customerInvoices, key = { it.id }) { inv ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInvoiceDetail(inv.id) },
                    shape = RoundedCornerShape(14.dp),
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
                                    text = inv.invoiceNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = FormatUtils.formatDateTime(inv.invoiceDateMillis, settings.dateFormatPattern, useBn),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            InvoiceStatusBadge(inv.status)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "সর্বমোট: ${FormatUtils.formatCurrency(inv.finalAmount, currency, useBn)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "পণ্য সংখ্যা: ${FormatUtils.formatNumber(inv.itemCount.toDouble(), useBn, 0)}টি • ওজন: ${FormatUtils.formatQuantity(inv.totalQuantity, "কেজি", useBn)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val invItems = allInvoiceItems
                                                .filter { it.invoiceId == inv.id }
                                                .sortedBy { it.sortOrder }
                                            val pdf = PdfAndPrintManager.generateInvoicePdfFile(
                                                context = context,
                                                invoice = inv,
                                                items = invItems,
                                                settings = settings
                                            )
                                            PdfAndPrintManager.printPdfFile(
                                                context = context,
                                                pdfFile = pdf,
                                                jobName = "Chalan_${inv.invoiceNumber}",
                                                isThermal80mm = settings.defaultPrintFormat == "THERMAL_80MM"
                                            )
                                        } catch (e: Exception) {
                                            onMessage(e.localizedMessage ?: "প্রিন্ট শুরু করতে সমস্যা হয়েছে")
                                        }
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = "পুনঃপ্রিন্ট", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("প্রিন্ট")
                                }
                                Button(
                                    onClick = { onOpenInvoiceDetail(inv.id) },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("খুলুন")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        CustomerEditorDialog(
            initialCustomer = customer,
            isBusy = isBusy,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                onSaveCustomer(updated) {
                    showEditDialog = false
                }
            }
        )
    }

    if (pickingStart) {
        BengaliDatePickerDialog(
            initialMillis = customStartMillis,
            onDateSelected = {
                customStartMillis = FormatUtils.startOfDayMillis(it)
                pickingStart = false
            },
            onDismiss = { pickingStart = false }
        )
    }
    if (pickingEnd) {
        BengaliDatePickerDialog(
            initialMillis = customEndMillis,
            onDateSelected = {
                customEndMillis = FormatUtils.endOfDayMillis(it)
                pickingEnd = false
            },
            onDismiss = { pickingEnd = false }
        )
    }
}

@Composable
fun CustomerEditorDialog(
    initialCustomer: Customer?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialCustomer?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(initialCustomer?.phone ?: "") }
    var address by rememberSaveable { mutableStateOf(initialCustomer?.address ?: "") }
    var notes by rememberSaveable { mutableStateOf(initialCustomer?.notes ?: "") }
    var isActive by rememberSaveable { mutableStateOf(initialCustomer?.isActive ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCustomer == null) "নতুন কাস্টমার যোগ করুন" else "কাস্টমারের তথ্য সম্পাদনা",
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
                if (errorMsg != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Strictly labeled "নাম" per Master Prompt Section 5 (not "Buyer")
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("নাম *") },
                    placeholder = { Text("কাস্টমার বা প্রতিষ্ঠানের নাম") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                    placeholder = { Text("01XXXXXXXXX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ঠিকানা (ঐচ্ছিক)") },
                    placeholder = { Text("বাজার / এলাকার নাম") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট / মন্তব্য (ঐচ্ছিক)") },
                    placeholder = { Text("অতিরিক্ত তথ্য") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("সক্রিয় কাস্টমার", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        errorMsg = "অনুগ্রহ করে কাস্টমারের নাম লিখুন"
                        return@Button
                    }
                    val draft = (initialCustomer ?: Customer()).copy(
                        name = trimmed,
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim(),
                        isActive = isActive
                    )
                    onSave(draft)
                },
                enabled = !isBusy,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_customer_confirm_button")
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
