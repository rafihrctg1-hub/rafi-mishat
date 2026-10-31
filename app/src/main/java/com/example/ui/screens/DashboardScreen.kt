package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.Expense
import com.example.data.model.Invoice
import com.example.data.model.Product
import com.example.data.model.ReportCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InvoiceStatusBadge
import com.example.ui.components.SummaryMetricCard
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ReportTab
import com.example.util.FormatUtils
import java.util.Calendar

private data class QuickActionItem(
    val title: String,
    val icon: ImageVector,
    val tag: String,
    val isPrimary: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    settings: BusinessSettings,
    invoices: List<Invoice>,
    expenses: List<Expense>,
    customers: List<Customer>,
    products: List<Product>,
    onStartNewInvoice: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenInvoiceDetail: (String) -> Unit
) {
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    val now = System.currentTimeMillis()
    val todayStart = remember(now / 60000L) { FormatUtils.startOfDayMillis(now) }
    val todayEnd = remember(now / 60000L) { FormatUtils.endOfDayMillis(now) }

    val monthBounds = remember(now / 3600000L) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val yr = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH)
        FormatUtils.startOfMonthMillis(yr, mo) to FormatUtils.endOfMonthMillis(yr, mo)
    }

    val todaySummary = remember(invoices, expenses, todayStart, todayEnd) {
        val todayInv = invoices.filter { it.invoiceDateMillis in todayStart..todayEnd }
        val todayExp = expenses.filter { it.expenseDateMillis in todayStart..todayEnd }
        ReportCalculator.calculateSummary(todayInv, todayExp)
    }

    val monthSales = remember(invoices, monthBounds) {
        invoices
            .filter { it.invoiceDateMillis in monthBounds.first..monthBounds.second }
            .sumOf { it.productSubtotal }
    }

    val quickActions = listOf(
        QuickActionItem(
            title = "নতুন চালান তৈরি",
            icon = Icons.Default.AddCircle,
            tag = "quick_new_invoice",
            isPrimary = true,
            onClick = onStartNewInvoice
        ),
        QuickActionItem(
            title = "চালান ইতিহাস",
            icon = Icons.Default.History,
            tag = "quick_invoice_history",
            onClick = { onNavigate(AppScreen.InvoiceHistory()) }
        ),
        QuickActionItem(
            title = "কাস্টমার তালিকা",
            icon = Icons.Default.People,
            tag = "quick_customers",
            onClick = { onNavigate(AppScreen.Customers) }
        ),
        QuickActionItem(
            title = "পণ্য তালিকা",
            icon = Icons.Default.Inventory2,
            tag = "quick_products",
            onClick = { onNavigate(AppScreen.Products) }
        ),
        QuickActionItem(
            title = "খরচ যোগ করুন",
            icon = Icons.Default.MoneyOff,
            tag = "quick_expenses",
            onClick = { onNavigate(AppScreen.Expenses) }
        ),
        QuickActionItem(
            title = "দৈনিক রিপোর্ট",
            icon = Icons.Default.Assessment,
            tag = "quick_daily_report",
            onClick = { onNavigate(AppScreen.Reports(ReportTab.DAILY)) }
        ),
        QuickActionItem(
            title = "মাসিক রিপোর্ট",
            icon = Icons.Default.CalendarMonth,
            tag = "quick_monthly_report",
            onClick = { onNavigate(AppScreen.Reports(ReportTab.MONTHLY)) }
        ),
        QuickActionItem(
            title = "সেটিংস",
            icon = Icons.Default.Settings,
            tag = "quick_settings",
            onClick = { onNavigate(AppScreen.Settings) }
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Business Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_market_banner_1793378902179),
                        contentDescription = stringResource(R.string.cd_market_banner),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xEE0B3E21),
                                        Color(0xCC146C3B),
                                        Color(0x880D4A28)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (settings.businessAddress.isNotBlank()) {
                                    Text(
                                        text = settings.businessAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.88f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = FormatUtils.formatDate(now, settings.dateFormatPattern, useBn),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "আজকের নিট ব্যবসায়িক আয়",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(todaySummary.netBusinessAmount, currency, useBn),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable(onClick = onStartNewInvoice)
                                    .testTag("hero_new_chalan_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "নতুন চালান",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Action Buttons (All 8 required actions)
        item {
            Text(
                text = "দ্রুত কার্যক্রম (Quick Actions)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quickActions.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { action ->
                            val bg = if (action.isPrimary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val fg = if (action.isPrimary) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = action.onClick)
                                    .testTag(action.tag),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = bg),
                                border = if (action.isPrimary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (action.isPrimary) {
                                                    Color.White.copy(alpha = 0.2f)
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = action.title,
                                            tint = if (action.isPrimary) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = fg,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // All 8 Required Summary Cards calculated from real saved database records
        item {
            Text(
                text = "ব্যবসায়িক সারসংক্ষেপ (আজ ও চলতি মাস)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetricCard(
                        title = "আজকের মোট বিক্রি",
                        value = FormatUtils.formatCurrency(todaySummary.totalProductSales, currency, useBn),
                        subtitle = FormatUtils.formatQuantity(todaySummary.totalQuantity, "কেজি", useBn),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.Reports(ReportTab.DAILY)) }
                    )
                    SummaryMetricCard(
                        title = "আজকের মোট চালান",
                        value = FormatUtils.formatNumber(todaySummary.invoiceCount.toDouble(), useBn, 0),
                        subtitle = "টি সংরক্ষিত চালান",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.InvoiceHistory()) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetricCard(
                        title = "আজকের মোট কমিশন",
                        value = FormatUtils.formatCurrency(todaySummary.totalCommission, currency, useBn),
                        subtitle = "ডিফল্ট হার: ${FormatUtils.formatNumber(settings.defaultCommissionRate, useBn)}%",
                        icon = Icons.Default.Percent,
                        accentColor = Color(0xFF0369A1),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "আজকের ম্যানুয়াল খরচ",
                        value = FormatUtils.formatCurrency(todaySummary.totalManualExpenses, currency, useBn),
                        subtitle = "খামালি, পরিবহন ও অন্যান্য",
                        icon = Icons.Default.MoneyOff,
                        accentColor = Color(0xFFB45309),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.Expenses) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetricCard(
                        title = "আজকের নিট টাকা",
                        value = FormatUtils.formatCurrency(todaySummary.netBusinessAmount, currency, useBn),
                        subtitle = "বিক্রি − কমিশন − খরচ",
                        icon = Icons.Default.AccountBalanceWallet,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.Reports(ReportTab.DAILY)) }
                    )
                    SummaryMetricCard(
                        title = "চলতি মাসের মোট বিক্রি",
                        value = FormatUtils.formatCurrency(monthSales, currency, useBn),
                        subtitle = "এই মাসের পণ্য বিক্রি",
                        icon = Icons.Default.CalendarMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.Reports(ReportTab.MONTHLY)) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetricCard(
                        title = "মোট নিবন্ধিত কাস্টমার",
                        value = FormatUtils.formatNumber(customers.size.toDouble(), useBn, 0),
                        subtitle = "সক্রিয়: ${FormatUtils.formatNumber(customers.count { it.isActive }.toDouble(), useBn, 0)} জন",
                        icon = Icons.Default.People,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.Customers) }
                    )
                    SummaryMetricCard(
                        title = "মোট নিবন্ধিত পণ্য",
                        value = FormatUtils.formatNumber(products.size.toDouble(), useBn, 0),
                        subtitle = "সক্রিয়: ${FormatUtils.formatNumber(products.count { it.isActive }.toDouble(), useBn, 0)} টি",
                        icon = Icons.Default.Inventory2,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.Products) }
                    )
                }
            }
        }

        // Recent Chalans Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "সাম্প্রতিক চালানসমূহ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (invoices.isNotEmpty()) {
                    TextButton(onClick = { onNavigate(AppScreen.InvoiceHistory()) }) {
                        Text("সব দেখুন")
                    }
                }
            }
        }

        if (invoices.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "এখনও কোনো চালান তৈরি করা হয়নি",
                    subtitle = "আপনার প্রথম সবজি পাইকারি চালান তৈরি করতে নিচের বাটনে ট্যাপ করুন।",
                    actionLabel = "নতুন চালান তৈরি করুন",
                    onActionClick = onStartNewInvoice
                )
            }
        } else {
            items(invoices.take(5), key = { it.id }) { inv ->
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = inv.invoiceNumber,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                InvoiceStatusBadge(inv.status)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "নাম: ${inv.customerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${FormatUtils.formatDateTime(inv.invoiceDateMillis, settings.dateFormatPattern, useBn)} • ${FormatUtils.formatNumber(inv.itemCount.toDouble(), useBn, 0)}টি পণ্য",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FormatUtils.formatCurrency(inv.finalAmount, currency, useBn),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = FormatUtils.formatQuantity(inv.totalQuantity, "কেজি", useBn),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
