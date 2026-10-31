package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.Product
import com.example.data.model.ReportCalculator
import com.example.ui.components.BengaliDatePickerDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InvoiceStatusBadge
import com.example.ui.components.SummaryMetricCard
import com.example.ui.viewmodel.ReportTab
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.util.FormatUtils
import com.example.util.PdfAndPrintManager
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    initialTab: ReportTab,
    invoices: List<Invoice>,
    invoiceItems: List<InvoiceItem>,
    expenses: List<Expense>,
    customers: List<Customer>,
    products: List<Product>,
    settings: BusinessSettings,
    onOpenInvoiceDetail: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var activeTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    val now = System.currentTimeMillis()
    val initCal = remember { Calendar.getInstance().apply { timeInMillis = now } }

    // Daily state
    var selectedDayMillis by rememberSaveable { mutableLongStateOf(now) }
    var pickingDailyDate by remember { mutableStateOf(false) }

    // Monthly state
    var selectedYear by rememberSaveable { mutableIntStateOf(initCal.get(Calendar.YEAR)) }
    var selectedMonthZeroBased by rememberSaveable { mutableIntStateOf(initCal.get(Calendar.MONTH)) }

    // Custom range state
    var customStartMillis by rememberSaveable {
        mutableLongStateOf(FormatUtils.startOfDayMillis(now - 7L * 86400000L))
    }
    var customEndMillis by rememberSaveable {
        mutableLongStateOf(FormatUtils.endOfDayMillis(now))
    }
    var pickingCustomStart by remember { mutableStateOf(false) }
    var pickingCustomEnd by remember { mutableStateOf(false) }

    // Additional filters: Customer, Product, Invoice Number
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var filterCustomerId by rememberSaveable { mutableStateOf("") }
    var filterProductName by rememberSaveable { mutableStateOf("") }
    var filterInvoiceNumber by rememberSaveable { mutableStateOf("") }

    var pendingReportPdf by remember { mutableStateOf<File?>(null) }
    val saveReportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = pendingReportPdf
        if (uri != null && file != null) {
            try {
                PdfAndPrintManager.copyPdfToUri(context, file, uri)
                onMessage("রিপোর্ট PDF সফলভাবে সংরক্ষিত হয়েছে")
            } catch (e: Exception) {
                onMessage(e.localizedMessage ?: "রিপোর্ট সংরক্ষণে সমস্যা হয়েছে")
            }
        }
    }

    val (rangeStart, rangeEnd, periodLabel) = remember(
        activeTab,
        selectedDayMillis,
        selectedYear,
        selectedMonthZeroBased,
        customStartMillis,
        customEndMillis,
        settings.dateFormatPattern,
        useBn
    ) {
        when (activeTab) {
            ReportTab.DAILY -> {
                val s = FormatUtils.startOfDayMillis(selectedDayMillis)
                val e = FormatUtils.endOfDayMillis(selectedDayMillis)
                val lbl = FormatUtils.formatDate(selectedDayMillis, settings.dateFormatPattern, useBn)
                Triple(s, e, lbl)
            }
            ReportTab.MONTHLY -> {
                val s = FormatUtils.startOfMonthMillis(selectedYear, selectedMonthZeroBased)
                val e = FormatUtils.endOfMonthMillis(selectedYear, selectedMonthZeroBased)
                val mName = FormatUtils.bengaliMonths.getOrElse(selectedMonthZeroBased) { "" }
                val yrStr = FormatUtils.formatNumber(selectedYear.toDouble(), useBn, 0).replace(",", "")
                Triple(s, e, "$mName $yrStr")
            }
            ReportTab.CUSTOM -> {
                val s = FormatUtils.startOfDayMillis(minOf(customStartMillis, customEndMillis))
                val e = FormatUtils.endOfDayMillis(maxOf(customStartMillis, customEndMillis))
                val lbl = "${FormatUtils.formatDate(s, settings.dateFormatPattern, useBn)} - ${FormatUtils.formatDate(e, settings.dateFormatPattern, useBn)}"
                Triple(s, e, lbl)
            }
        }
    }

    val invoiceIdsMatchingProduct = remember(invoiceItems, filterProductName) {
        if (filterProductName.isBlank()) {
            null
        } else {
            val q = filterProductName.trim().lowercase()
            invoiceItems
                .filter { it.productName.lowercase().contains(q) }
                .map { it.invoiceId }
                .toSet()
        }
    }

    val filteredInvoices = remember(
        invoices,
        rangeStart,
        rangeEnd,
        filterCustomerId,
        invoiceIdsMatchingProduct,
        filterInvoiceNumber
    ) {
        val invNumQ = filterInvoiceNumber.trim().lowercase()
        invoices.filter { inv ->
            val inTime = inv.invoiceDateMillis in rangeStart..rangeEnd
            val matchesCustomer = filterCustomerId.isBlank() || inv.customerId == filterCustomerId
            val matchesProduct = invoiceIdsMatchingProduct == null || inv.id in invoiceIdsMatchingProduct
            val matchesInvNo = invNumQ.isEmpty() || inv.invoiceNumber.lowercase().contains(invNumQ)
            inTime && matchesCustomer && matchesProduct && matchesInvNo
        }
    }

    val filteredExpenses = remember(expenses, rangeStart, rangeEnd, filterInvoiceNumber) {
        val invNumQ = filterInvoiceNumber.trim().lowercase()
        expenses.filter { exp ->
            val inTime = exp.expenseDateMillis in rangeStart..rangeEnd
            val matchesInv = invNumQ.isEmpty() || exp.relatedInvoiceNumber.lowercase().contains(invNumQ)
            inTime && matchesInv
        }
    }

    val reportSummary = remember(filteredInvoices, filteredExpenses) {
        ReportCalculator.calculateSummary(filteredInvoices, filteredExpenses)
    }

    val reportTitleBn = when (activeTab) {
        ReportTab.DAILY -> "দৈনিক ব্যবসা রিপোর্ট"
        ReportTab.MONTHLY -> "মাসিক ব্যবসা রিপোর্ট"
        ReportTab.CUSTOM -> "কাস্টম তারিখ রিপোর্ট"
    }

    val distinctProductNames = remember(products, invoiceItems) {
        (products.map { it.nameBn } + invoiceItems.map { it.productName })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column {
                    PrimaryTabRow(
                        selectedTabIndex = activeTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = activeTab == ReportTab.DAILY,
                            onClick = { activeTab = ReportTab.DAILY },
                            text = { Text("দৈনিক", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = activeTab == ReportTab.MONTHLY,
                            onClick = { activeTab = ReportTab.MONTHLY },
                            text = { Text("মাসিক", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = activeTab == ReportTab.CUSTOM,
                            onClick = { activeTab = ReportTab.CUSTOM },
                            text = { Text("কাস্টম তারিখ", fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    Column(modifier = Modifier.padding(14.dp)) {
                        when (activeTab) {
                            ReportTab.DAILY -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { pickingDailyDate = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("তারিখ: $periodLabel")
                                    }
                                    FilterChip(
                                        selected = FormatUtils.startOfDayMillis(selectedDayMillis) == FormatUtils.startOfDayMillis(now),
                                        onClick = { selectedDayMillis = now },
                                        label = { Text("আজ") }
                                    )
                                    FilterChip(
                                        selected = FormatUtils.startOfDayMillis(selectedDayMillis) == FormatUtils.startOfDayMillis(now - 86400000L),
                                        onClick = { selectedDayMillis = now - 86400000L },
                                        label = { Text("গতকাল") }
                                    )
                                }
                            }
                            ReportTab.MONTHLY -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (selectedMonthZeroBased == 0) {
                                                selectedMonthZeroBased = 11
                                                selectedYear -= 1
                                            } else {
                                                selectedMonthZeroBased -= 1
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "পূর্ববর্তী মাস")
                                    }
                                    Text(
                                        text = periodLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            if (selectedMonthZeroBased == 11) {
                                                selectedMonthZeroBased = 0
                                                selectedYear += 1
                                            } else {
                                                selectedMonthZeroBased += 1
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "পরবর্তী মাস")
                                    }
                                }
                            }
                            ReportTab.CUSTOM -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { pickingCustomStart = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("শুরু: ${FormatUtils.formatDate(customStartMillis, settings.dateFormatPattern, useBn)}")
                                    }
                                    OutlinedButton(
                                        onClick = { pickingCustomEnd = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("শেষ: ${FormatUtils.formatDate(customEndMillis, settings.dateFormatPattern, useBn)}")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showAdvancedFilters = !showAdvancedFilters },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (showAdvancedFilters) "ফিল্টার লুকান" else "কাস্টমার / পণ্য / চালান ফিল্টার")
                            }

                            if (filterCustomerId.isNotBlank() || filterProductName.isNotBlank() || filterInvoiceNumber.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        filterCustomerId = ""
                                        filterProductName = ""
                                        filterInvoiceNumber = ""
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("রিসেট")
                                }
                            }
                        }

                        if (showAdvancedFilters) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = filterInvoiceNumber,
                                onValueChange = { filterInvoiceNumber = it },
                                label = { Text("চালান নম্বর দিয়ে ফিল্টার") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (customers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("কাস্টমার নির্বাচন:", style = MaterialTheme.typography.labelMedium)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = filterCustomerId.isBlank(),
                                        onClick = { filterCustomerId = "" },
                                        label = { Text("সব কাস্টমার") }
                                    )
                                    customers.forEach { c ->
                                        FilterChip(
                                            selected = filterCustomerId == c.id,
                                            onClick = { filterCustomerId = c.id },
                                            label = { Text(c.name) }
                                        )
                                    }
                                }
                            }

                            if (distinctProductNames.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("সবজি / পণ্য নির্বাচন:", style = MaterialTheme.typography.labelMedium)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = filterProductName.isBlank(),
                                        onClick = { filterProductName = "" },
                                        label = { Text("সব পণ্য") }
                                    )
                                    distinctProductNames.forEach { pName ->
                                        FilterChip(
                                            selected = filterProductName == pName,
                                            onClick = { filterProductName = pName },
                                            label = { Text(pName) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Net Business Amount Hero Card (Exact Formula: Product Sales Subtotal - Commission - Manual Expenses)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "নিট ব্যবসায়িক আয় (Net Business Amount)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "সূত্র: পণ্য বিক্রি − সংরক্ষিত কমিশন − ম্যানুয়াল খরচ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = FormatUtils.formatCurrency(reportSummary.netBusinessAmount, currency, useBn),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("report_net_business_amount")
                    )
                }
            }
        }

        // 4 Core Breakdown Metric Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryMetricCard(
                        title = "মোট চালান সংখ্যা",
                        value = FormatUtils.formatNumber(reportSummary.invoiceCount.toDouble(), useBn, 0),
                        subtitle = "ওজন: ${FormatUtils.formatQuantity(reportSummary.totalQuantity, "কেজি", useBn)}",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "মোট পণ্য বিক্রি (Subtotal)",
                        value = FormatUtils.formatCurrency(reportSummary.totalProductSales, currency, useBn),
                        subtitle = "মূল সবজি বিক্রি",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryMetricCard(
                        title = "মোট কমিশন",
                        value = FormatUtils.formatCurrency(reportSummary.totalCommission, currency, useBn),
                        subtitle = "চালানে ধার্যকৃত কমিশন",
                        icon = Icons.Default.Percent,
                        accentColor = Color(0xFF047857),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "ম্যানুয়াল খরচ",
                        value = FormatUtils.formatCurrency(reportSummary.totalManualExpenses, currency, useBn),
                        subtitle = "${FormatUtils.formatNumber(filteredExpenses.size.toDouble(), useBn, 0)}টি খরচ এন্ট্রি",
                        icon = Icons.Default.MoneyOff,
                        accentColor = Color(0xFFB45309),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Separate Reconciliation Box for Additional Charges & Discounts + Export Actions
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "হিসাব সমন্বয় (Reconciliation) ও PDF প্রিন্ট",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ReconcileRow(
                        label = "অতিরিক্ত চার্জ (আলাদা দেখানো হয়েছে):",
                        value = "+${FormatUtils.formatCurrency(reportSummary.totalAdditionalCharges, currency, useBn)}"
                    )
                    ReconcileRow(
                        label = "প্রদত্ত ছাড় / ডিসকাউন্ট (আলাদা দেখানো হয়েছে):",
                        value = "-${FormatUtils.formatCurrency(reportSummary.totalDiscounts, currency, useBn)}"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ReconcileRow(
                        label = "সর্বমোট চালান বিল (Final Invoice Total):",
                        value = FormatUtils.formatCurrency(reportSummary.totalFinalInvoiceAmount, currency, useBn),
                        bold = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val pdf = PdfAndPrintManager.generateReportPdfFile(
                                        context = context,
                                        reportTitle = reportTitleBn,
                                        periodLabel = periodLabel,
                                        summary = reportSummary,
                                        invoices = filteredInvoices,
                                        expenses = filteredExpenses,
                                        settings = settings
                                    )
                                    PdfAndPrintManager.printPdfFile(
                                        context = context,
                                        pdfFile = pdf,
                                        jobName = "Report_${activeTab.name}"
                                    )
                                } catch (e: Exception) {
                                    onMessage(e.localizedMessage ?: "রিপোর্ট প্রিন্ট করতে সমস্যা হয়েছে")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("print_report_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রিন্ট")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val pdf = PdfAndPrintManager.generateReportPdfFile(
                                        context = context,
                                        reportTitle = reportTitleBn,
                                        periodLabel = periodLabel,
                                        summary = reportSummary,
                                        invoices = filteredInvoices,
                                        expenses = filteredExpenses,
                                        settings = settings
                                    )
                                    pendingReportPdf = pdf
                                    saveReportPdfLauncher.launch("Khandaker_Report_${activeTab.name}.pdf")
                                } catch (e: Exception) {
                                    onMessage(e.localizedMessage ?: "PDF তৈরি করতে সমস্যা হয়েছে")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF সেভ")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val pdf = PdfAndPrintManager.generateReportPdfFile(
                                        context = context,
                                        reportTitle = reportTitleBn,
                                        periodLabel = periodLabel,
                                        summary = reportSummary,
                                        invoices = filteredInvoices,
                                        expenses = filteredExpenses,
                                        settings = settings
                                    )
                                    PdfAndPrintManager.sharePdfFile(context, pdf, "$reportTitleBn ($periodLabel)")
                                } catch (e: Exception) {
                                    onMessage(e.localizedMessage ?: "শেয়ার করতে সমস্যা হয়েছে")
                                }
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "শেয়ার রিপোর্ট", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Filtered Invoices List
        item {
            Text(
                text = "চালান বিবরণী (${FormatUtils.formatNumber(filteredInvoices.size.toDouble(), useBn, 0)}টি)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (filteredInvoices.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Assessment,
                    title = "এই সময়ে কোনো চালান নেই",
                    subtitle = "তারিখ বা ফিল্টার পরিবর্তন করে পুনরায় দেখুন।"
                )
            }
        } else {
            items(filteredInvoices, key = { it.id }) { inv ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInvoiceDetail(inv.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${inv.invoiceNumber} • ${inv.customerName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = FormatUtils.formatDateTime(inv.invoiceDateMillis, settings.dateFormatPattern, useBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "পণ্য মূল: ${FormatUtils.formatCurrency(inv.productSubtotal, currency, useBn)} | কমিশন: ${FormatUtils.formatCurrency(inv.commissionAmount, currency, useBn)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FormatUtils.formatCurrency(inv.finalAmount, currency, useBn),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            InvoiceStatusBadge(inv.status)
                        }
                    }
                }
            }
        }

        if (filteredExpenses.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ম্যানুয়াল খরচ বিবরণী (${FormatUtils.formatNumber(filteredExpenses.size.toDouble(), useBn, 0)}টি)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(filteredExpenses, key = { "exp_${it.id}" }) { exp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${ExpenseCategory.fromKey(exp.category).labelBn} — ${exp.description}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = FormatUtils.formatDate(exp.expenseDateMillis, settings.dateFormatPattern, useBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = FormatUtils.formatCurrency(exp.amount, currency, useBn),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (pickingDailyDate) {
        BengaliDatePickerDialog(
            initialMillis = selectedDayMillis,
            onDateSelected = {
                selectedDayMillis = it
                pickingDailyDate = false
            },
            onDismiss = { pickingDailyDate = false }
        )
    }
    if (pickingCustomStart) {
        BengaliDatePickerDialog(
            initialMillis = customStartMillis,
            onDateSelected = {
                customStartMillis = FormatUtils.startOfDayMillis(it)
                pickingCustomStart = false
            },
            onDismiss = { pickingCustomStart = false }
        )
    }
    if (pickingCustomEnd) {
        BengaliDatePickerDialog(
            initialMillis = customEndMillis,
            onDateSelected = {
                customEndMillis = FormatUtils.endOfDayMillis(it)
                pickingCustomEnd = false
            },
            onDismiss = { pickingCustomEnd = false }
        )
    }
}

@Composable
private fun ReconcileRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
