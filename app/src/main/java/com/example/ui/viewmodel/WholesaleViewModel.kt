package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.DraftInvoiceLine
import com.example.data.model.Expense
import com.example.data.model.Invoice
import com.example.data.model.InvoiceCalculator
import com.example.data.model.InvoiceItem
import com.example.data.model.InvoiceTotalsSummary
import com.example.data.model.Product
import com.example.data.repository.WholesaleRepository
import com.example.util.FormatUtils
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ReportTab {
    DAILY, MONTHLY, CUSTOM
}

sealed interface AppScreen {
    data object Dashboard : AppScreen
    data object CreateOrEditInvoice : AppScreen
    data class InvoiceHistory(val filterCustomerId: String = "") : AppScreen
    data class InvoiceDetail(val invoiceId: String) : AppScreen
    data object Customers : AppScreen
    data class CustomerDetail(val customerId: String) : AppScreen
    data object Products : AppScreen
    data object Expenses : AppScreen
    data class Reports(val initialTab: ReportTab = ReportTab.DAILY) : AppScreen
    data object Settings : AppScreen
}

data class InvoiceFormState(
    val editingInvoice: Invoice? = null,
    val invoiceNumber: String = "",
    val selectedCustomerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: String = "",
    val invoiceDateMillis: Long = System.currentTimeMillis(),
    val lines: List<DraftInvoiceLine> = emptyList(),
    val commissionEnabled: Boolean = true,
    val commissionRateText: String = "7",
    val additionalChargesText: String = "",
    val discountText: String = "",
    val status: String = "SAVED",
    val notes: String = ""
) {
    val commissionRate: Double
        get() = (FormatUtils.parseFlexibleDouble(commissionRateText) ?: 0.0).coerceIn(0.0, 100.0)

    val additionalCharges: Double
        get() = (FormatUtils.parseFlexibleDouble(additionalChargesText) ?: 0.0).coerceAtLeast(0.0)

    val discount: Double
        get() = (FormatUtils.parseFlexibleDouble(discountText) ?: 0.0).coerceAtLeast(0.0)

    val totals: InvoiceTotalsSummary
        get() = InvoiceCalculator.calculateTotals(
            lines = lines,
            commissionEnabled = commissionEnabled,
            commissionRate = commissionRate,
            additionalCharges = additionalCharges,
            discount = discount
        )
}

class WholesaleViewModel(
    private val repository: WholesaleRepository,
    val currentUserId: String,
    val currentUserEmail: String
) : ViewModel() {

    // Navigation Backstack
    private val _screenStack = MutableStateFlow<List<AppScreen>>(listOf(AppScreen.Dashboard))
    val currentScreen: StateFlow<AppScreen> = _screenStack
        .map { it.lastOrNull() ?: AppScreen.Dashboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppScreen.Dashboard)

    val canNavigateBack: StateFlow<Boolean> = _screenStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Busy / Submission lock & Snackbar messages
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    // Real-time Firestore Streams with Two-Tier Kotlin Flow Error Handling
    val settingsState: StateFlow<BusinessSettings> = repository.observeSettings(currentUserId)
        .catch { e ->
            Log.w(TAG, "Error observing settings", e)
            emit(BusinessSettings(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BusinessSettings(userId = currentUserId)
        )

    val productsState: StateFlow<UiState<List<Product>>> = repository.observeProducts(currentUserId)
        .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing products", e)
            emit(UiState.Error(e.localizedMessage ?: "পণ্য তালিকা লোড করতে সমস্যা হয়েছে"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val customersState: StateFlow<UiState<List<Customer>>> = repository.observeCustomers(currentUserId)
        .map<List<Customer>, UiState<List<Customer>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing customers", e)
            emit(UiState.Error(e.localizedMessage ?: "কাস্টমার তালিকা লোড করতে সমস্যা হয়েছে"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val invoicesState: StateFlow<UiState<List<Invoice>>> = repository.observeInvoices(currentUserId)
        .map<List<Invoice>, UiState<List<Invoice>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing invoices", e)
            emit(UiState.Error(e.localizedMessage ?: "চালান তালিকা লোড করতে সমস্যা হয়েছে"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val invoiceItemsState: StateFlow<List<InvoiceItem>> = repository.observeInvoiceItems(currentUserId)
        .catch { e ->
            Log.w(TAG, "Error observing invoice items", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val expensesState: StateFlow<UiState<List<Expense>>> = repository.observeExpenses(currentUserId)
        .map<List<Expense>, UiState<List<Expense>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing expenses", e)
            emit(UiState.Error(e.localizedMessage ?: "খরচ তালিকা লোড করতে সমস্যা হয়েছে"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Invoice Draft Form State (preserved across product additions and screen rotations)
    private val _invoiceForm = MutableStateFlow(InvoiceFormState())
    val invoiceForm: StateFlow<InvoiceFormState> = _invoiceForm.asStateFlow()

    // -------------------- NAVIGATION --------------------

    fun navigateToRoot(screen: AppScreen) {
        if (screen is AppScreen.CreateOrEditInvoice && _invoiceForm.value.editingInvoice == null && _invoiceForm.value.invoiceNumber.isBlank()) {
            prepareFreshInvoiceDraft()
        }
        _screenStack.value = if (screen == AppScreen.Dashboard) {
            listOf(AppScreen.Dashboard)
        } else {
            listOf(AppScreen.Dashboard, screen)
        }
    }

    fun pushScreen(screen: AppScreen) {
        _screenStack.update { stack -> stack + screen }
    }

    fun navigateBack() {
        _screenStack.update { stack ->
            if (stack.size > 1) stack.dropLast(1) else listOf(AppScreen.Dashboard)
        }
    }

    // -------------------- INVOICE DRAFT MANAGEMENT --------------------

    fun startNewInvoice(preselectedCustomer: Customer? = null) {
        prepareFreshInvoiceDraft(preselectedCustomer)
        pushScreen(AppScreen.CreateOrEditInvoice)
    }

    fun prepareFreshInvoiceDraft(preselectedCustomer: Customer? = null) {
        val settings = settingsState.value
        val existingInvoices = (invoicesState.value as? UiState.Success)?.data ?: emptyList()
        val nextNum = repository.generateNextInvoiceNumber(settings, existingInvoices)
        val rateStr = FormatUtils.formatNumber(settings.defaultCommissionRate, useBengaliNumerals = false, maxDecimals = 2)

        _invoiceForm.value = InvoiceFormState(
            editingInvoice = null,
            invoiceNumber = nextNum,
            selectedCustomerId = preselectedCustomer?.id ?: "",
            customerName = preselectedCustomer?.name ?: "",
            customerPhone = preselectedCustomer?.phone ?: "",
            deliveryAddress = preselectedCustomer?.address ?: "",
            invoiceDateMillis = System.currentTimeMillis(),
            lines = emptyList(),
            commissionEnabled = settings.commissionEnabled,
            commissionRateText = rateStr,
            additionalChargesText = "",
            discountText = "",
            status = "SAVED",
            notes = ""
        )
    }

    fun startEditInvoice(invoice: Invoice) {
        val items = invoiceItemsState.value
            .filter { it.invoiceId == invoice.id }
            .sortedBy { it.sortOrder }
            .map { item ->
                DraftInvoiceLine(
                    localLineId = item.id.ifBlank { UUID.randomUUID().toString() },
                    existingDocId = item.id,
                    existingCreatedAt = item.createdAt,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    unit = item.unit,
                    rate = item.rate
                )
            }

        _invoiceForm.value = InvoiceFormState(
            editingInvoice = invoice,
            invoiceNumber = invoice.invoiceNumber,
            selectedCustomerId = invoice.customerId,
            customerName = invoice.customerName,
            customerPhone = invoice.customerPhone,
            deliveryAddress = invoice.deliveryAddress,
            invoiceDateMillis = invoice.invoiceDateMillis,
            lines = items,
            commissionEnabled = invoice.commissionEnabled,
            commissionRateText = FormatUtils.formatNumber(invoice.commissionRate, useBengaliNumerals = false),
            additionalChargesText = if (invoice.additionalCharges > 0.0) FormatUtils.formatNumber(invoice.additionalCharges, useBengaliNumerals = false) else "",
            discountText = if (invoice.discount > 0.0) FormatUtils.formatNumber(invoice.discount, useBengaliNumerals = false) else "",
            status = invoice.status,
            notes = invoice.notes
        )
        pushScreen(AppScreen.CreateOrEditInvoice)
    }

    fun updateInvoiceHeader(
        invoiceNumber: String? = null,
        selectedCustomer: Customer? = null,
        clearSavedCustomer: Boolean = false,
        customerName: String? = null,
        customerPhone: String? = null,
        deliveryAddress: String? = null,
        invoiceDateMillis: Long? = null,
        status: String? = null,
        notes: String? = null
    ) {
        _invoiceForm.update { cur ->
            var next = cur
            if (invoiceNumber != null) next = next.copy(invoiceNumber = invoiceNumber)
            if (selectedCustomer != null) {
                next = next.copy(
                    selectedCustomerId = selectedCustomer.id,
                    customerName = selectedCustomer.name,
                    customerPhone = selectedCustomer.phone,
                    deliveryAddress = selectedCustomer.address
                )
            } else if (clearSavedCustomer) {
                next = next.copy(selectedCustomerId = "")
            }
            if (customerName != null) next = next.copy(customerName = customerName)
            if (customerPhone != null) next = next.copy(customerPhone = customerPhone)
            if (deliveryAddress != null) next = next.copy(deliveryAddress = deliveryAddress)
            if (invoiceDateMillis != null) next = next.copy(invoiceDateMillis = invoiceDateMillis)
            if (status != null) next = next.copy(status = status)
            if (notes != null) next = next.copy(notes = notes)
            next
        }
    }

    fun updateInvoiceFinancials(
        commissionEnabled: Boolean? = null,
        commissionRateText: String? = null,
        additionalChargesText: String? = null,
        discountText: String? = null
    ) {
        _invoiceForm.update { cur ->
            cur.copy(
                commissionEnabled = commissionEnabled ?: cur.commissionEnabled,
                commissionRateText = commissionRateText ?: cur.commissionRateText,
                additionalChargesText = additionalChargesText ?: cur.additionalChargesText,
                discountText = discountText ?: cur.discountText
            )
        }
    }

    /**
     * Appends a new line with a distinct localLineId. Never merges lines even if productName matches!
     */
    fun addDraftProductLine(
        productId: String,
        productName: String,
        quantity: Double,
        unit: String,
        rate: Double
    ) {
        if (productName.isBlank() || quantity <= 0.0 || rate < 0.0) {
            postMessage("সঠিক পণ্যের নাম, পরিমাণ (> ০) এবং দর প্রদান করুন")
            return
        }
        val newLine = DraftInvoiceLine(
            localLineId = UUID.randomUUID().toString(),
            productId = productId,
            productName = productName.trim(),
            quantity = InvoiceCalculator.round2(quantity),
            unit = unit.trim().ifBlank { "কেজি" },
            rate = InvoiceCalculator.round2(rate)
        )
        _invoiceForm.update { cur -> cur.copy(lines = cur.lines + newLine) }
    }

    fun updateDraftProductLine(
        localLineId: String,
        productName: String,
        quantity: Double,
        unit: String,
        rate: Double
    ) {
        if (productName.isBlank() || quantity <= 0.0 || rate < 0.0) {
            postMessage("সঠিক পণ্যের নাম, পরিমাণ (> ০) এবং দর প্রদান করুন")
            return
        }
        _invoiceForm.update { cur ->
            cur.copy(
                lines = cur.lines.map { line ->
                    if (line.localLineId == localLineId) {
                        line.copy(
                            productName = productName.trim(),
                            quantity = InvoiceCalculator.round2(quantity),
                            unit = unit.trim().ifBlank { "কেজি" },
                            rate = InvoiceCalculator.round2(rate)
                        )
                    } else {
                        line
                    }
                }
            )
        }
    }

    fun removeDraftProductLine(localLineId: String) {
        _invoiceForm.update { cur ->
            cur.copy(lines = cur.lines.filterNot { it.localLineId == localLineId })
        }
    }

    fun saveInvoice(openDetailAfterSave: Boolean = true) {
        if (_isBusy.value) return
        val form = _invoiceForm.value
        if (form.lines.isEmpty()) {
            postMessage("চালানে অন্তত একটি সবজি পণ্য যোগ করুন")
            return
        }
        if (form.invoiceNumber.isBlank()) {
            postMessage("চালান নম্বর প্রদান করুন")
            return
        }
        val totals = form.totals
        val grossBeforeDiscount = totals.productSubtotal + totals.commissionAmount + totals.additionalCharges
        if (totals.discount > grossBeforeDiscount + 0.001) {
            postMessage("ছাড়ের পরিমাণ মোট বিলের চেয়ে বেশি হতে পারবে না")
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            try {
                val editing = form.editingInvoice
                val savedId = if (editing == null) {
                    val draft = Invoice(
                        invoiceNumber = form.invoiceNumber.trim(),
                        customerId = form.selectedCustomerId,
                        customerName = form.customerName.trim().ifBlank { "নগদ ক্রেতা" },
                        customerPhone = form.customerPhone.trim(),
                        deliveryAddress = form.deliveryAddress.trim(),
                        invoiceDateMillis = form.invoiceDateMillis,
                        commissionEnabled = form.commissionEnabled,
                        commissionRate = form.commissionRate,
                        additionalCharges = form.additionalCharges,
                        discount = form.discount,
                        status = form.status,
                        notes = form.notes.trim()
                    )
                    val id = repository.createInvoiceWithItems(draft, form.lines, settingsState.value)
                    postMessage("নতুন চালান সফলভাবে সংরক্ষিত হয়েছে")
                    id
                } else {
                    val updated = editing.copy(
                        invoiceNumber = form.invoiceNumber.trim(),
                        customerId = form.selectedCustomerId,
                        customerName = form.customerName.trim().ifBlank { "নগদ ক্রেতা" },
                        customerPhone = form.customerPhone.trim(),
                        deliveryAddress = form.deliveryAddress.trim(),
                        invoiceDateMillis = form.invoiceDateMillis,
                        commissionEnabled = form.commissionEnabled,
                        commissionRate = form.commissionRate,
                        additionalCharges = form.additionalCharges,
                        discount = form.discount,
                        status = form.status,
                        notes = form.notes.trim()
                    )
                    repository.updateInvoiceWithItems(updated, form.lines)
                    postMessage("চালান সফলভাবে আপডেট করা হয়েছে")
                    editing.id
                }

                prepareFreshInvoiceDraft()
                if (openDetailAfterSave) {
                    _screenStack.update { stack ->
                        val base = stack.filterNot { it is AppScreen.CreateOrEditInvoice }
                        (if (base.isEmpty()) listOf(AppScreen.Dashboard) else base) + AppScreen.InvoiceDetail(savedId)
                    }
                } else {
                    navigateBack()
                }
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "চালান সংরক্ষণ করতে সমস্যা হয়েছে")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteInvoice(invoiceId: String, onDeleted: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.deleteInvoice(invoiceId)
                postMessage("চালান মুছে ফেলা হয়েছে")
                onDeleted()
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "চালান মুছতে সমস্যা হয়েছে")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------- PRODUCT CRUD --------------------

    fun saveProduct(product: Product, onSuccess: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                if (product.id.isBlank()) {
                    repository.addProduct(product)
                    postMessage("নতুন পণ্য যোগ করা হয়েছে")
                } else {
                    repository.updateProduct(product)
                    postMessage("পণ্যের তথ্য আপডেট করা হয়েছে")
                }
                onSuccess()
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "পণ্য সংরক্ষণে ত্রুটি")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun toggleProductActive(product: Product) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.updateProduct(product.copy(isActive = !product.isActive))
                postMessage(if (!product.isActive) "পণ্য সক্রিয় করা হয়েছে" else "পণ্য নিষ্ক্রিয় করা হয়েছে")
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "স্ট্যাটাস পরিবর্তনে ত্রুটি")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun seedStarterVegetables() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.seedCommonBangladeshiVegetables()
                postMessage("প্রাথমিক সবজি তালিকা যোগ করা হয়েছে")
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "তালিকা যোগ করতে সমস্যা হয়েছে")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------- CUSTOMER CRUD --------------------

    fun saveCustomer(customer: Customer, onSavedId: (String) -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val id = if (customer.id.isBlank()) {
                    val createdId = repository.addCustomer(customer)
                    postMessage("নতুন কাস্টমার যোগ করা হয়েছে")
                    createdId
                } else {
                    repository.updateCustomer(customer)
                    postMessage("কাস্টমারের তথ্য আপডেট করা হয়েছে")
                    customer.id
                }
                onSavedId(id)
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "কাস্টমার সংরক্ষণে ত্রুটি")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun toggleCustomerActive(customer: Customer) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.updateCustomer(customer.copy(isActive = !customer.isActive))
                postMessage(if (!customer.isActive) "কাস্টমার সক্রিয় করা হয়েছে" else "কাস্টমার নিষ্ক্রিয় করা হয়েছে")
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "কাস্টমার আপডেটে ত্রুটি")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------- EXPENSE CRUD --------------------

    fun saveExpense(expense: Expense, onSuccess: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                if (expense.id.isBlank()) {
                    repository.addExpense(expense)
                    postMessage("খরচ হিসাব সংরক্ষিত হয়েছে")
                } else {
                    repository.updateExpense(expense)
                    postMessage("খরচ হিসাব আপডেট করা হয়েছে")
                }
                onSuccess()
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "খরচ সংরক্ষণে ত্রুটি")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.deleteExpense(expenseId)
                postMessage("খরচ মুছে ফেলা হয়েছে")
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "খরচ মুছতে সমস্যা হয়েছে")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------- SETTINGS --------------------

    fun saveBusinessSettings(settings: BusinessSettings) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.saveSettings(settings)
                postMessage("সেটিংস সফলভাবে সংরক্ষিত হয়েছে")
            } catch (e: Exception) {
                postMessage(e.localizedMessage ?: "সেটিংস সংরক্ষণে ত্রুটি")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun postMessage(msg: String) {
        _messages.tryEmit(msg)
    }

    private companion object {
        const val TAG = "WholesaleVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
