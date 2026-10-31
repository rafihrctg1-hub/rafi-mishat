package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.ui.components.BengaliDatePickerDialog
import com.example.ui.components.ConfirmDestructiveDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InvoiceStatusBadge
import com.example.util.FormatUtils
import com.example.util.PdfAndPrintManager
import java.io.File
import java.util.Calendar

private enum class HistoryDateFilter(val labelBn: String) {
    ALL("সব"),
    TODAY("আজ"),
    YESTERDAY("গতকাল"),
    THIS_WEEK("এই সপ্তাহ"),
    THIS_MONTH("এই মাস"),
    CUSTOM("কাস্টম তারিখ")
}

@Composable
fun InvoiceHistoryScreen(
    invoices: List<Invoice>,
    settings: BusinessSettings,
    initialFilterCustomerId: String = "",
    onOpenInvoice: (String) -> Unit,
    onCreateNewInvoice: () -> Unit
) {
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var dateFilter by rememberSaveable { mutableStateOf(HistoryDateFilter.ALL) }
    var customStartMillis by rememberSaveable { mutableLongStateOf(FormatUtils.startOfDayMillis(System.currentTimeMillis() - 7 * 86400000L)) }
    var customEndMillis by rememberSaveable { mutableLongStateOf(FormatUtils.endOfDayMillis(System.currentTimeMillis())) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    val filteredInvoices = remember(invoices, searchQuery, dateFilter, customStartMillis, customEndMillis, initialFilterCustomerId) {
        val now = System.currentTimeMillis()
        val todayStart = FormatUtils.startOfDayMillis(now)
        val todayEnd = FormatUtils.endOfDayMillis(now)
        val yesterdayStart = FormatUtils.startOfDayMillis(now - 86400000L)
        val yesterdayEnd = FormatUtils.endOfDayMillis(now - 86400000L)
        val weekStart = FormatUtils.startOfDayMillis(now - 6 * 86400000L)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val monthStart = FormatUtils.startOfMonthMillis(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))

        invoices.filter { inv ->
            val customerMatch = initialFilterCustomerId.isBlank() || inv.customerId == initialFilterCustomerId
            val queryMatch = searchQuery.isBlank() ||
                inv.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                inv.customerName.contains(searchQuery, ignoreCase = true)
            val dateMatch = when (dateFilter) {
                HistoryDateFilter.ALL -> true
                HistoryDateFilter.TODAY -> inv.invoiceDateMillis in todayStart..todayEnd
                HistoryDateFilter.YESTERDAY -> inv.invoiceDateMillis in yesterdayStart..yesterdayEnd
                HistoryDateFilter.THIS_WEEK -> inv.invoiceDateMillis in weekStart..todayEnd
                HistoryDateFilter.THIS_MONTH -> inv.invoiceDateMillis in monthStart..todayEnd
                HistoryDateFilter.CUSTOM -> inv.invoiceDateMillis in customStartMillis..customEndMillis
            }
            customerMatch && queryMatch && dateMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("invoice_history_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("চালান নম্বর বা কাস্টমারের নাম দিয়ে খুঁজুন") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("invoice_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryDateFilter.entries.forEach { filter ->
                FilterChip(
                    selected = dateFilter == filter,
                    onClick = { dateFilter = filter },
                    label = { Text(filter.labelBn) }
                )
            }
        }

        if (dateFilter == HistoryDateFilter.CUSTOM) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickingStart = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("শুরু: ${FormatUtils.formatDate(customStartMillis, settings.dateFormatPattern, useBn)}")
                }
                OutlinedButton(
                    onClick = { pickingEnd = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("শেষ: ${FormatUtils.formatDate(customEndMillis, settings.dateFormatPattern, useBn)}")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "মোট চালান পাওয়া গেছে: ${FormatUtils.formatNumber(filteredInvoices.size.toDouble(), useBn, 0)} টি",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (filteredInvoices.isEmpty()) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "কোনো চালান পাওয়া যায়নি",
                subtitle = "ফিল্টার পরিবর্তন করুন অথবা নতুন একটি চালান তৈরি করুন।",
                actionLabel = "নতুন চালান তৈরি",
                onActionClick = onCreateNewInvoice
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredInvoices, key = { it.id }) { inv ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenInvoice(inv.id) }
                            .testTag("history_invoice_card_${inv.invoiceNumber}"),
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
                                Text(
                                    text = inv.invoiceNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                InvoiceStatusBadge(inv.status)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "নাম: ${inv.customerName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = FormatUtils.formatDate(inv.invoiceDateMillis, settings.dateFormatPattern, useBn),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "পণ্য: ${FormatUtils.formatNumber(inv.itemCount.toDouble(), useBn, 0)}টি (${FormatUtils.formatQuantity(inv.totalQuantity, "কেজি", useBn)})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "পণ্য মূল: ${FormatUtils.formatCurrency(inv.productSubtotal, currency, useBn)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "কমিশন: ${FormatUtils.formatCurrency(inv.commissionAmount, currency, useBn)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "সর্বমোট: ${FormatUtils.formatCurrency(inv.finalAmount, currency, useBn)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
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

// -------------------- PRINTABLE CHALAN DETAIL & PDF ACTIONS --------------------

@Composable
fun InvoiceDetailScreen(
    invoice: Invoice?,
    items: List<InvoiceItem>,
    settings: BusinessSettings,
    onEditInvoice: (Invoice) -> Unit,
    onDeleteInvoice: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var selectedPrintLayout by rememberSaveable(settings.defaultPrintFormat) {
        mutableStateOf(settings.defaultPrintFormat)
    }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = pendingSaveFile
        if (uri != null && file != null) {
            try {
                PdfAndPrintManager.copyPdfToUri(context, file, uri)
                onMessage("PDF চালান সফলভাবে সংরক্ষিত হয়েছে")
            } catch (e: Exception) {
                onMessage(e.localizedMessage ?: "PDF সংরক্ষণে সমস্যা হয়েছে")
            }
        }
    }

    if (invoice == null) {
        Column(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "চালান পাওয়া যায়নি",
                subtitle = "চালানটি মুছে ফেলা হয়েছে বা লোড হচ্ছে।"
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("invoice_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Print Format Toggle (Standard A4 vs 80mm Thermal) & Action Toolbar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("প্রিন্ট ও PDF ফরম্যাট:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedPrintLayout == "A4",
                                onClick = { selectedPrintLayout = "A4" },
                                label = { Text("A4 স্ট্যান্ডার্ড") }
                            )
                            FilterChip(
                                selected = selectedPrintLayout == "THERMAL_80MM",
                                onClick = { selectedPrintLayout = "THERMAL_80MM" },
                                label = { Text("80mm থার্মাল") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val pdf = PdfAndPrintManager.generateInvoicePdfFile(
                                        context, invoice, items, settings, selectedPrintLayout
                                    )
                                    PdfAndPrintManager.printPdfFile(
                                        context,
                                        pdf,
                                        jobName = "Chalan_${invoice.invoiceNumber}",
                                        isThermal80mm = selectedPrintLayout == "THERMAL_80MM"
                                    )
                                } catch (e: Exception) {
                                    onMessage(e.localizedMessage ?: "প্রিন্ট চালু করা যায়নি")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("print_invoice_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রিন্ট")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val pdf = PdfAndPrintManager.generateInvoicePdfFile(
                                        context, invoice, items, settings, selectedPrintLayout
                                    )
                                    pendingSaveFile = pdf
                                    savePdfLauncher.launch("${invoice.invoiceNumber}.pdf")
                                } catch (e: Exception) {
                                    onMessage(e.localizedMessage ?: "PDF তৈরি করা যায়নি")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_pdf_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF সেভ")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val pdf = PdfAndPrintManager.generateInvoicePdfFile(
                                        context, invoice, items, settings, selectedPrintLayout
                                    )
                                    PdfAndPrintManager.sharePdfFile(
                                        context,
                                        pdf,
                                        shareTitle = "চালান ${invoice.invoiceNumber} — ${invoice.customerName}"
                                    )
                                } catch (e: Exception) {
                                    onMessage(e.localizedMessage ?: "শেয়ার করা যায়নি")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_pdf_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("শেয়ার")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEditInvoice(invoice) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("edit_saved_invoice_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("চালান এডিট করুন")
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("delete_invoice_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("মুছে ফেলুন")
                        }
                    }
                }
            }
        }

        // Clean White Printable Chalan Sheet Preview (Adapts width if 80mm thermal selected)
        item {
            val maxSheetWidth = if (selectedPrintLayout == "THERMAL_80MM") 320.dp else 620.dp
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxSheetWidth),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFF146C3B).copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header
                    val bizName = invoice.businessNameSnapshot.ifBlank {
                        settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" }
                    }
                    Text(
                        text = bizName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF146C3B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val addr = invoice.businessAddressSnapshot.ifBlank { settings.businessAddress }
                    if (addr.isNotBlank()) {
                        Text(
                            text = addr,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val ph = invoice.businessPhoneSnapshot.ifBlank { settings.businessPhone }
                    if (ph.isNotBlank()) {
                        Text(
                            text = "মোবাইল: $ph",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF146C3B), thickness = 1.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("নাম: ${invoice.customerName}", fontWeight = FontWeight.Bold, color = Color.Black)
                            if (invoice.customerPhone.isNotBlank()) {
                                Text("মোবাইল: ${invoice.customerPhone}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                            }
                            if (invoice.deliveryAddress.isNotBlank()) {
                                Text("ঠিকানা: ${invoice.deliveryAddress}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("চালান: ${invoice.invoiceNumber}", fontWeight = FontWeight.Bold, color = Color(0xFF146C3B))
                            Text(
                                text = FormatUtils.formatDateTime(invoice.invoiceDateMillis, settings.dateFormatPattern, useBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            InvoiceStatusBadge(invoice.status)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Items Table
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF146C3B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        Text("পণ্য", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.3f))
                        Text("পরিমাণ", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
                        Text("দর", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
                        Text("মোট", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1.05f))
                    }

                    items.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (idx % 2 == 1) Color(0xFFF9FAFB) else Color.White)
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = item.productName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = FormatUtils.formatQuantity(item.quantity, item.unit, useBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                text = FormatUtils.formatCurrency(item.rate, currency, useBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.85f)
                            )
                            Text(
                                text = FormatUtils.formatCurrency(item.lineTotal, currency, useBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.05f)
                            )
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Totals
                    ChalanTotalRow("মোট ওজন / পরিমাণ:", FormatUtils.formatQuantity(invoice.totalQuantity, "কেজি", useBn))
                    ChalanTotalRow("পণ্যের মোট মূল্য:", FormatUtils.formatCurrency(invoice.productSubtotal, currency, useBn), bold = true)
                    if (invoice.commissionEnabled) {
                        ChalanTotalRow(
                            "কমিশন (${FormatUtils.formatNumber(invoice.commissionRate, useBn)}%):",
                            FormatUtils.formatCurrency(invoice.commissionAmount, currency, useBn)
                        )
                    }
                    if (invoice.additionalCharges > 0.0) {
                        ChalanTotalRow("অতিরিক্ত চার্জ:", FormatUtils.formatCurrency(invoice.additionalCharges, currency, useBn))
                    }
                    if (invoice.discount > 0.0) {
                        ChalanTotalRow("ছাড় (Discount):", "-${FormatUtils.formatCurrency(invoice.discount, currency, useBn)}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFECFDF3),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF146C3B).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("সর্বমোট প্রদেয় টাকা:", fontWeight = FontWeight.Bold, color = Color(0xFF146C3B))
                            Text(
                                text = FormatUtils.formatCurrency(invoice.finalAmount, currency, useBn),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF146C3B)
                            )
                        }
                    }

                    if (invoice.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("নোট: ${invoice.notes}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("__________________\nক্রেতার স্বাক্ষর", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, textAlign = TextAlign.Center)
                        Text("__________________\nবিক্রেতার স্বাক্ষর", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDestructiveDialog(
            title = "চালান মুছে ফেলবেন?",
            message = "আপনি কি নিশ্চিত যে চালান ${invoice.invoiceNumber} স্থায়ীভাবে মুছে ফেলতে চান? এর ফলে রিপোর্ট থেকেও এই চালানের হিসাব বাদ যাবে।",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteInvoice(invoice.id)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun ChalanTotalRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = Color(0xFF374151)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = Color.Black
        )
    }
}
