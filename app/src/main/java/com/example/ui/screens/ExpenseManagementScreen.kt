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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoneyOff
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Invoice
import com.example.ui.components.BengaliDatePickerDialog
import com.example.ui.components.ConfirmDestructiveDialog
import com.example.ui.components.EmptyStateCard
import com.example.util.FormatUtils
import java.util.Calendar

private enum class ExpenseDateFilter(val labelBn: String) {
    TODAY("আজ"),
    THIS_MONTH("এই মাস"),
    ALL("সব তারিখ"),
    CUSTOM("কাস্টম তারিখ")
}

@Composable
fun ExpenseManagementScreen(
    expenses: List<Expense>,
    recentInvoices: List<Invoice>,
    settings: BusinessSettings,
    isBusy: Boolean,
    onSaveExpense: (Expense, () -> Unit) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    val useBn = settings.useBengaliNumerals
    val currency = settings.currencySymbol.ifBlank { "৳" }

    var dateFilter by rememberSaveable { mutableStateOf(ExpenseDateFilter.THIS_MONTH) }
    var selectedCategoryKey by rememberSaveable { mutableStateOf("ALL") }
    var customStartMillis by rememberSaveable {
        mutableLongStateOf(FormatUtils.startOfDayMillis(System.currentTimeMillis() - 7L * 86400000L))
    }
    var customEndMillis by rememberSaveable {
        mutableLongStateOf(FormatUtils.endOfDayMillis(System.currentTimeMillis()))
    }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var deletingExpense by remember { mutableStateOf<Expense?>(null) }

    val filteredExpenses = remember(expenses, dateFilter, selectedCategoryKey, customStartMillis, customEndMillis) {
        val now = System.currentTimeMillis()
        val todayStart = FormatUtils.startOfDayMillis(now)
        val todayEnd = FormatUtils.endOfDayMillis(now)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val monthStart = FormatUtils.startOfMonthMillis(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        val monthEnd = FormatUtils.endOfMonthMillis(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))

        expenses.filter { exp ->
            val inDate = when (dateFilter) {
                ExpenseDateFilter.TODAY -> exp.expenseDateMillis in todayStart..todayEnd
                ExpenseDateFilter.THIS_MONTH -> exp.expenseDateMillis in monthStart..monthEnd
                ExpenseDateFilter.ALL -> true
                ExpenseDateFilter.CUSTOM -> exp.expenseDateMillis in customStartMillis..customEndMillis
            }
            val inCat = selectedCategoryKey == "ALL" || exp.category == selectedCategoryKey
            inDate && inCat
        }
    }

    val filteredTotal = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("expense_management_screen"),
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
                                text = "ম্যানুয়াল ব্যবসায়িক খরচ",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "পরিবহন, লেবার/খামালি, বাজার ও আড়ত খরচ হিসাব",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingExpense = null
                                showEditor = true
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_expense_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("খরচ যোগ করুন")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "নির্বাচিত সময়ের মোট খরচ",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "মোট এন্ট্রি: ${FormatUtils.formatNumber(filteredExpenses.size.toDouble(), useBn, 0)}টি",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(filteredTotal, currency, useBn),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpenseDateFilter.entries.forEach { f ->
                            FilterChip(
                                selected = dateFilter == f,
                                onClick = { dateFilter = f },
                                label = { Text(f.labelBn) }
                            )
                        }
                    }

                    if (dateFilter == ExpenseDateFilter.CUSTOM) {
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

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryKey == "ALL",
                            onClick = { selectedCategoryKey = "ALL" },
                            label = { Text("সব খাত") }
                        )
                        ExpenseCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryKey == cat.key,
                                onClick = { selectedCategoryKey = cat.key },
                                label = { Text(cat.labelBn) }
                            )
                        }
                    }
                }
            }
        }

        if (filteredExpenses.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.MoneyOff,
                    title = "কোনো খরচের হিসাব নেই",
                    subtitle = "চালানে কোনো স্বয়ংক্রিয় লেবার বা খামালি খরচ কাটা হয় না। ব্যবসার প্রকৃত খরচ জানতে এখানে ম্যানুয়াল খরচ যোগ করুন।",
                    actionLabel = "নতুন খরচ যোগ করুন",
                    onActionClick = {
                        editingExpense = null
                        showEditor = true
                    }
                )
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                val cat = ExpenseCategory.fromKey(expense.category)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_card_${expense.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = cat.labelBn,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatDate(expense.expenseDateMillis, settings.dateFormatPattern, useBn),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = expense.description,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (expense.relatedInvoiceNumber.isNotBlank()) {
                                Text(
                                    text = "সম্পর্কিত চালান: ${expense.relatedInvoiceNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (expense.notes.isNotBlank()) {
                                Text(
                                    text = "নোট: ${expense.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FormatUtils.formatCurrency(expense.amount, currency, useBn),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        editingExpense = expense
                                        showEditor = true
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "খরচ সম্পাদনা",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { deletingExpense = expense },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "খরচ মুছুন",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ExpenseEditorDialog(
            initialExpense = editingExpense,
            recentInvoices = recentInvoices,
            settings = settings,
            isBusy = isBusy,
            onDismiss = { showEditor = false },
            onSave = { draft ->
                onSaveExpense(draft) {
                    showEditor = false
                }
            }
        )
    }

    if (deletingExpense != null) {
        ConfirmDestructiveDialog(
            title = "খরচ মুছে ফেলবেন?",
            message = "\"${deletingExpense!!.description}\" (${FormatUtils.formatCurrency(deletingExpense!!.amount, currency, useBn)}) মুছে ফেললে তা রিপোর্ট থেকে বাদ যাবে।",
            onConfirm = {
                val id = deletingExpense!!.id
                deletingExpense = null
                onDeleteExpense(id)
            },
            onDismiss = { deletingExpense = null }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditorDialog(
    initialExpense: Expense?,
    recentInvoices: List<Invoice>,
    settings: BusinessSettings,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    val useBn = settings.useBengaliNumerals

    var expenseDateMillis by rememberSaveable {
        mutableLongStateOf(initialExpense?.expenseDateMillis ?: System.currentTimeMillis())
    }
    var categoryKey by rememberSaveable {
        mutableStateOf(initialExpense?.category ?: ExpenseCategory.LABOR.key)
    }
    var description by rememberSaveable { mutableStateOf(initialExpense?.description ?: "") }
    var amountText by rememberSaveable {
        mutableStateOf(
            if (initialExpense != null && initialExpense.amount > 0.0) {
                FormatUtils.formatNumber(initialExpense.amount, useBengaliNumerals = false)
            } else {
                ""
            }
        )
    }
    var relatedInvoiceId by rememberSaveable { mutableStateOf(initialExpense?.relatedInvoiceId ?: "") }
    var relatedInvoiceNumber by rememberSaveable { mutableStateOf(initialExpense?.relatedInvoiceNumber ?: "") }
    var notes by rememberSaveable { mutableStateOf(initialExpense?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialExpense == null) "নতুন খরচ যোগ করুন" else "খরচ সম্পাদনা করুন",
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

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("তারিখ: ${FormatUtils.formatDate(expenseDateMillis, settings.dateFormatPattern, useBn)}")
                }

                Text(
                    text = "খরচের খাত নির্বাচন করুন:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = categoryKey == cat.key,
                            onClick = { categoryKey = cat.key },
                            label = { Text(cat.labelBn) }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        errorMsg = null
                    },
                    label = { Text("খরচের বিবরণ *") },
                    placeholder = { Text("যেমন: ট্রাক আনলোডিং লেবার / বাজার খাজনা") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_description_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMsg = null
                    },
                    label = { Text("খরচের পরিমাণ (৳) *") },
                    placeholder = { Text("যেমন: ৫০০ বা 500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                if (recentInvoices.isNotEmpty()) {
                    Text(
                        text = "সম্পর্কিত চালান (ঐচ্ছিক):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = relatedInvoiceId.isBlank(),
                            onClick = {
                                relatedInvoiceId = ""
                                relatedInvoiceNumber = ""
                            },
                            label = { Text("সাধারণ খরচ") }
                        )
                        recentInvoices.take(12).forEach { inv ->
                            FilterChip(
                                selected = relatedInvoiceId == inv.id,
                                onClick = {
                                    relatedInvoiceId = inv.id
                                    relatedInvoiceNumber = inv.invoiceNumber
                                },
                                label = { Text(inv.invoiceNumber) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val desc = description.trim()
                    if (desc.isBlank()) {
                        errorMsg = "অনুগ্রহ করে খরচের বিবরণ লিখুন"
                        return@Button
                    }
                    val amt = FormatUtils.parseFlexibleDouble(amountText)
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "খরচের পরিমাণ অবশ্যই ০ এর বেশি হতে হবে"
                        return@Button
                    }
                    val draft = (initialExpense ?: Expense()).copy(
                        expenseDateMillis = expenseDateMillis,
                        category = categoryKey,
                        description = desc,
                        amount = amt,
                        relatedInvoiceId = relatedInvoiceId,
                        relatedInvoiceNumber = relatedInvoiceNumber,
                        notes = notes.trim()
                    )
                    onSave(draft)
                },
                enabled = !isBusy,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_expense_confirm_button")
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

    if (showDatePicker) {
        BengaliDatePickerDialog(
            initialMillis = expenseDateMillis,
            onDateSelected = {
                expenseDateMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
