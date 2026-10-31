package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.DraftInvoiceLine
import com.example.data.model.Expense
import com.example.data.model.Invoice
import com.example.data.model.InvoiceCalculator
import com.example.data.model.InvoiceItem
import com.example.data.model.Product
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WholesaleRepository(
    private val db: FirebaseFirestore,
    private val authProvider: () -> FirebaseAuth = { Firebase.auth }
) {
    // Convenience constructor using the provisioned named Firestore database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return authProvider().currentUser?.uid
            ?: throw IllegalStateException("ব্যবহারকারী লগইন করা নেই (User must be signed in with Google).")
    }

    // -------------------- REAL-TIME OBSERVATION FLOWS --------------------

    fun observeSettings(userId: String): Flow<BusinessSettings> {
        val path = "users/$userId/settings/business_config"
        return db.collection("users").document(userId)
            .collection("settings").document("business_config")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        BusinessSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: BusinessSettings(userId = userId)
                } else {
                    BusinessSettings(userId = userId)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    fun observeProducts(userId: String): Flow<List<Product>> {
        val path = "users/$userId/products"
        return db.collection("users").document(userId)
            .collection("products")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Product::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.nameBn }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeCustomers(userId: String): Flow<List<Customer>> {
        val path = "users/$userId/customers"
        return db.collection("users").document(userId)
            .collection("customers")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Customer::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.name }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeInvoices(userId: String): Flow<List<Invoice>> {
        val path = "users/$userId/invoices"
        return db.collection("users").document(userId)
            .collection("invoices")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Invoice::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.invoiceDateMillis }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeInvoiceItems(userId: String): Flow<List<InvoiceItem>> {
        val path = "users/$userId/invoice_items"
        return db.collection("users").document(userId)
            .collection("invoice_items")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    InvoiceItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.sortOrder }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeExpenses(userId: String): Flow<List<Expense>> {
        val path = "users/$userId/expenses"
        return db.collection("users").document(userId)
            .collection("expenses")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Expense::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.expenseDateMillis }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // -------------------- SETTINGS --------------------

    suspend fun saveSettings(settings: BusinessSettings) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("settings").document("business_config")
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existingObj = existingSnap.toObject(
                    BusinessSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val preservedCreatedAt = existingObj?.createdAt ?: existingSnap.getTimestamp("createdAt")
                docRef.set(
                    settings.copy(
                        id = "business_config",
                        userId = uid,
                        businessName = settings.businessName.trim().ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                        invoicePrefix = settings.invoicePrefix.trim().ifBlank { "KVT" },
                        nextInvoiceSeq = settings.nextInvoiceSeq.coerceAtLeast(1L),
                        createdAt = preservedCreatedAt,
                        updatedAt = null
                    )
                ).await()
            } else {
                docRef.set(
                    settings.copy(
                        id = "business_config",
                        userId = uid,
                        businessName = settings.businessName.trim().ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                        invoicePrefix = settings.invoicePrefix.trim().ifBlank { "KVT" },
                        nextInvoiceSeq = settings.nextInvoiceSeq.coerceAtLeast(1L),
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // -------------------- PRODUCTS --------------------

    suspend fun addProduct(product: Product): String {
        val uid = requireUserId()
        require(product.nameBn.isNotBlank()) { "পণ্যের বাংলা নাম আবশ্যক" }
        require(product.defaultRate >= 0.0) { "পণ্যের দর ঋণাত্মক হতে পারবে না" }

        val docId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(uid).collection("products").document(docId)
        val payload = product.copy(
            id = docId,
            userId = uid,
            nameBn = product.nameBn.trim(),
            nameEn = product.nameEn.trim(),
            defaultUnit = product.defaultUnit.trim().ifBlank { "কেজি" },
            defaultRate = InvoiceCalculator.round2(product.defaultRate),
            notes = product.notes.trim(),
            createdAt = null,
            updatedAt = null
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateProduct(product: Product) {
        val uid = requireUserId()
        require(product.id.isNotBlank()) { "Product ID missing" }
        require(product.nameBn.isNotBlank()) { "পণ্যের বাংলা নাম আবশ্যক" }
        require(product.defaultRate >= 0.0) { "পণ্যের দর ঋণাত্মক হতে পারবে না" }
        requireNotNull(product.createdAt) { "Product createdAt must not be null for update" }

        val docRef = db.collection("users").document(uid).collection("products").document(product.id)
        try {
            docRef.set(
                product.copy(
                    userId = uid,
                    nameBn = product.nameBn.trim(),
                    nameEn = product.nameEn.trim(),
                    defaultUnit = product.defaultUnit.trim().ifBlank { "কেজি" },
                    defaultRate = InvoiceCalculator.round2(product.defaultRate),
                    notes = product.notes.trim(),
                    updatedAt = null
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProduct(productId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("products").document(productId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // -------------------- CUSTOMERS --------------------

    suspend fun addCustomer(customer: Customer): String {
        val uid = requireUserId()
        require(customer.name.isNotBlank()) { "কাস্টমারের নাম আবশ্যক" }

        val docId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(uid).collection("customers").document(docId)
        val payload = customer.copy(
            id = docId,
            userId = uid,
            name = customer.name.trim(),
            phone = customer.phone.trim(),
            address = customer.address.trim(),
            notes = customer.notes.trim(),
            createdAt = null,
            updatedAt = null
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateCustomer(customer: Customer) {
        val uid = requireUserId()
        require(customer.id.isNotBlank()) { "Customer ID missing" }
        require(customer.name.isNotBlank()) { "কাস্টমারের নাম আবশ্যক" }
        requireNotNull(customer.createdAt) { "Customer createdAt must not be null for update" }

        val docRef = db.collection("users").document(uid).collection("customers").document(customer.id)
        try {
            docRef.set(
                customer.copy(
                    userId = uid,
                    name = customer.name.trim(),
                    phone = customer.phone.trim(),
                    address = customer.address.trim(),
                    notes = customer.notes.trim(),
                    updatedAt = null
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCustomer(customerId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("customers").document(customerId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // -------------------- INVOICES & CHALANS --------------------

    fun generateNextInvoiceNumber(settings: BusinessSettings, existingInvoices: List<Invoice>): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val prefix = settings.invoicePrefix.trim().ifBlank { "KVT" }
        val headerPrefix = "$prefix-$year-".uppercase()

        val maxExistingSeq = existingInvoices.mapNotNull { inv ->
            val upper = inv.invoiceNumber.trim().uppercase()
            if (upper.startsWith(headerPrefix)) {
                upper.removePrefix(headerPrefix).toLongOrNull()
            } else {
                null
            }
        }.maxOrNull() ?: 0L

        var candidateSeq = maxOf(settings.nextInvoiceSeq.coerceAtLeast(1L), maxExistingSeq + 1L)
        val existingNumbers = existingInvoices.map { it.invoiceNumber.trim().uppercase() }.toSet()

        var candidate = "$prefix-$year-${candidateSeq.toString().padStart(4, '0')}"
        while (existingNumbers.contains(candidate.uppercase())) {
            candidateSeq++
            candidate = "$prefix-$year-${candidateSeq.toString().padStart(4, '0')}"
        }
        return candidate
    }

    suspend fun createInvoiceWithItems(
        invoiceDraft: Invoice,
        lines: List<DraftInvoiceLine>,
        currentSettings: BusinessSettings
    ): String {
        val uid = requireUserId()
        require(lines.isNotEmpty()) { "চালানে অন্তত একটি পণ্য যোগ করুন" }
        lines.forEach { line ->
            require(line.productName.isNotBlank()) { "পণ্যের নাম খালি রাখা যাবে না" }
            require(line.quantity > 0.0) { "পরিমাণ শূন্য বা ঋণাত্মক হতে পারবে না" }
            require(line.rate >= 0.0) { "দর ঋণাত্মক হতে পারবে না" }
        }

        val invoicesCollection = db.collection("users").document(uid).collection("invoices")
        val itemsCollection = db.collection("users").document(uid).collection("invoice_items")
        val settingsDoc = db.collection("users").document(uid).collection("settings").document("business_config")

        try {
            // Check duplicate invoice number for this user
            val duplicateQuery = invoicesCollection
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val normalizedNumber = invoiceDraft.invoiceNumber.trim()
            require(normalizedNumber.isNotBlank()) { "চালান নম্বর আবশ্যক" }
            val hasDuplicate = duplicateQuery.documents.any {
                it.getString("invoiceNumber")?.equals(normalizedNumber, ignoreCase = true) == true
            }
            require(!hasDuplicate) { "এই চালান নম্বর ($normalizedNumber) ইতিমধ্যে ব্যবহৃত হয়েছে" }

            val totals = InvoiceCalculator.calculateTotals(
                lines = lines,
                commissionEnabled = invoiceDraft.commissionEnabled,
                commissionRate = invoiceDraft.commissionRate,
                additionalCharges = invoiceDraft.additionalCharges,
                discount = invoiceDraft.discount
            )

            val invoiceId = UUID.randomUUID().toString()
            val invoiceDate = if (invoiceDraft.invoiceDateMillis > 0L) {
                invoiceDraft.invoiceDateMillis
            } else {
                System.currentTimeMillis()
            }

            val invoiceToSave = invoiceDraft.copy(
                id = invoiceId,
                userId = uid,
                invoiceNumber = normalizedNumber,
                customerName = invoiceDraft.customerName.trim().ifBlank { "নগদ ক্রেতা" },
                customerPhone = invoiceDraft.customerPhone.trim(),
                deliveryAddress = invoiceDraft.deliveryAddress.trim(),
                invoiceDateMillis = invoiceDate,
                itemCount = totals.itemCount.toLong(),
                totalQuantity = totals.totalQuantity,
                productSubtotal = totals.productSubtotal,
                commissionEnabled = totals.commissionEnabled,
                commissionRate = totals.commissionRate,
                commissionAmount = totals.commissionAmount,
                additionalCharges = totals.additionalCharges,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                notes = invoiceDraft.notes.trim(),
                businessNameSnapshot = currentSettings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                businessAddressSnapshot = currentSettings.businessAddress,
                businessPhoneSnapshot = currentSettings.businessPhone,
                createdAt = null,
                updatedAt = null
            )

            val settingsSnap = settingsDoc.get().await()
            val batch = db.batch()
            batch.set(invoicesCollection.document(invoiceId), invoiceToSave)

            // Preserve EVERY line separately even when product names match!
            lines.forEachIndexed { index, line ->
                val itemId = UUID.randomUUID().toString()
                val itemDoc = InvoiceItem(
                    id = itemId,
                    userId = uid,
                    invoiceId = invoiceId,
                    productId = line.productId,
                    productName = line.productName.trim(),
                    quantity = InvoiceCalculator.round2(line.quantity),
                    unit = line.unit.trim().ifBlank { "কেজি" },
                    rate = InvoiceCalculator.round2(line.rate),
                    lineTotal = line.lineTotal,
                    sortOrder = index.toLong(),
                    invoiceDateMillis = invoiceDate,
                    createdAt = null,
                    updatedAt = null
                )
                batch.set(itemsCollection.document(itemId), itemDoc)
            }

            // Increment nextInvoiceSeq in settings
            val nextSeq = (currentSettings.nextInvoiceSeq.coerceAtLeast(1L)) + 1L
            if (settingsSnap.exists()) {
                val existingCreatedAt = settingsSnap.getTimestamp(
                    "createdAt",
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                batch.set(
                    settingsDoc,
                    currentSettings.copy(
                        id = "business_config",
                        userId = uid,
                        nextInvoiceSeq = nextSeq,
                        createdAt = existingCreatedAt,
                        updatedAt = null
                    )
                )
            } else {
                batch.set(
                    settingsDoc,
                    currentSettings.copy(
                        id = "business_config",
                        userId = uid,
                        nextInvoiceSeq = nextSeq,
                        createdAt = null,
                        updatedAt = null
                    )
                )
            }

            batch.commit().await()
            return invoiceId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, invoicesCollection.path)
            throw e
        }
    }

    suspend fun updateInvoiceWithItems(
        existingInvoice: Invoice,
        lines: List<DraftInvoiceLine>
    ) {
        val uid = requireUserId()
        require(existingInvoice.id.isNotBlank()) { "Invoice ID missing" }
        requireNotNull(existingInvoice.createdAt) { "Invoice createdAt must not be null for update" }
        require(lines.isNotEmpty()) { "চালানে অন্তত একটি পণ্য থাকতে হবে" }

        val invoicesCollection = db.collection("users").document(uid).collection("invoices")
        val itemsCollection = db.collection("users").document(uid).collection("invoice_items")

        try {
            val allInvoicesSnap = invoicesCollection.whereEqualTo("userId", uid).get().await()
            val normalizedNumber = existingInvoice.invoiceNumber.trim()
            val hasDuplicate = allInvoicesSnap.documents.any { doc ->
                doc.id != existingInvoice.id &&
                    doc.getString("invoiceNumber")?.equals(normalizedNumber, ignoreCase = true) == true
            }
            require(!hasDuplicate) { "এই চালান নম্বর ($normalizedNumber) অন্য একটি চালানে ব্যবহৃত হয়েছে" }

            val existingItemsSnap = itemsCollection
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val oldItemsForInvoice = existingItemsSnap.documents.filter {
                it.getString("invoiceId") == existingInvoice.id
            }

            val totals = InvoiceCalculator.calculateTotals(
                lines = lines,
                commissionEnabled = existingInvoice.commissionEnabled,
                commissionRate = existingInvoice.commissionRate,
                additionalCharges = existingInvoice.additionalCharges,
                discount = existingInvoice.discount
            )

            val updatedInvoice = existingInvoice.copy(
                userId = uid,
                invoiceNumber = normalizedNumber,
                customerName = existingInvoice.customerName.trim().ifBlank { "নগদ ক্রেতা" },
                customerPhone = existingInvoice.customerPhone.trim(),
                deliveryAddress = existingInvoice.deliveryAddress.trim(),
                itemCount = totals.itemCount.toLong(),
                totalQuantity = totals.totalQuantity,
                productSubtotal = totals.productSubtotal,
                commissionEnabled = totals.commissionEnabled,
                commissionRate = totals.commissionRate,
                commissionAmount = totals.commissionAmount,
                additionalCharges = totals.additionalCharges,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                notes = existingInvoice.notes.trim(),
                updatedAt = null
            )

            val batch = db.batch()
            batch.set(invoicesCollection.document(existingInvoice.id), updatedInvoice)

            val retainedDocIds = lines.mapNotNull { it.existingDocId.takeIf { id -> id.isNotBlank() } }.toSet()
            oldItemsForInvoice.forEach { oldDoc ->
                if (!retainedDocIds.contains(oldDoc.id)) {
                    batch.delete(oldDoc.reference)
                }
            }

            lines.forEachIndexed { index, line ->
                if (line.existingDocId.isNotBlank() && line.existingCreatedAt != null) {
                    val updatedItem = InvoiceItem(
                        id = line.existingDocId,
                        userId = uid,
                        invoiceId = existingInvoice.id,
                        productId = line.productId,
                        productName = line.productName.trim(),
                        quantity = InvoiceCalculator.round2(line.quantity),
                        unit = line.unit.trim().ifBlank { "কেজি" },
                        rate = InvoiceCalculator.round2(line.rate),
                        lineTotal = line.lineTotal,
                        sortOrder = index.toLong(),
                        invoiceDateMillis = existingInvoice.invoiceDateMillis,
                        createdAt = line.existingCreatedAt,
                        updatedAt = null
                    )
                    batch.set(itemsCollection.document(line.existingDocId), updatedItem)
                } else {
                    val newItemId = UUID.randomUUID().toString()
                    val newItem = InvoiceItem(
                        id = newItemId,
                        userId = uid,
                        invoiceId = existingInvoice.id,
                        productId = line.productId,
                        productName = line.productName.trim(),
                        quantity = InvoiceCalculator.round2(line.quantity),
                        unit = line.unit.trim().ifBlank { "কেজি" },
                        rate = InvoiceCalculator.round2(line.rate),
                        lineTotal = line.lineTotal,
                        sortOrder = index.toLong(),
                        invoiceDateMillis = existingInvoice.invoiceDateMillis,
                        createdAt = null,
                        updatedAt = null
                    )
                    batch.set(itemsCollection.document(newItemId), newItem)
                }
            }

            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invoicesCollection.document(existingInvoice.id).path)
            throw e
        }
    }

    suspend fun deleteInvoice(invoiceId: String) {
        val uid = requireUserId()
        val invoiceRef = db.collection("users").document(uid).collection("invoices").document(invoiceId)
        val itemsCollection = db.collection("users").document(uid).collection("invoice_items")
        try {
            val allItemsSnap = itemsCollection.whereEqualTo("userId", uid).get().await()
            val batch = db.batch()
            allItemsSnap.documents
                .filter { it.getString("invoiceId") == invoiceId }
                .forEach { batch.delete(it.reference) }
            batch.delete(invoiceRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, invoiceRef.path)
            throw e
        }
    }

    suspend fun getCustomerById(targetUserId: String, customerId: String): Customer? {
        val docRef = db.collection("users").document(targetUserId)
            .collection("customers").document(customerId)
        try {
            return docRef.get().await().toObject(
                Customer::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    // -------------------- MANUAL EXPENSES --------------------

    suspend fun addExpense(expense: Expense): String {
        val uid = requireUserId()
        require(expense.description.isNotBlank()) { "খরচের বিবরণ আবশ্যক" }
        require(expense.amount > 0.0) { "খরচের পরিমাণ শূন্যের বেশি হতে হবে" }

        val docId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(uid).collection("expenses").document(docId)
        val payload = expense.copy(
            id = docId,
            userId = uid,
            expenseDateMillis = if (expense.expenseDateMillis > 0L) expense.expenseDateMillis else System.currentTimeMillis(),
            description = expense.description.trim(),
            amount = InvoiceCalculator.round2(expense.amount),
            relatedInvoiceId = expense.relatedInvoiceId.trim(),
            relatedInvoiceNumber = expense.relatedInvoiceNumber.trim(),
            notes = expense.notes.trim(),
            createdAt = null,
            updatedAt = null
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateExpense(expense: Expense) {
        val uid = requireUserId()
        require(expense.id.isNotBlank()) { "Expense ID missing" }
        require(expense.description.isNotBlank()) { "খরচের বিবরণ আবশ্যক" }
        require(expense.amount > 0.0) { "খরচের পরিমাণ শূন্যের বেশি হতে হবে" }
        requireNotNull(expense.createdAt) { "Expense createdAt must not be null for update" }

        val docRef = db.collection("users").document(uid).collection("expenses").document(expense.id)
        try {
            docRef.set(
                expense.copy(
                    userId = uid,
                    description = expense.description.trim(),
                    amount = InvoiceCalculator.round2(expense.amount),
                    relatedInvoiceId = expense.relatedInvoiceId.trim(),
                    relatedInvoiceNumber = expense.relatedInvoiceNumber.trim(),
                    notes = expense.notes.trim(),
                    updatedAt = null
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("expenses").document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    /**
     * Optional one-tap starter vegetable catalog helper for brand new wholesalers,
     * triggered ONLY when the user explicitly taps "প্রাথমিক সবজি তালিকা যোগ করুন" on an empty catalog.
     */
    suspend fun seedCommonBangladeshiVegetables() {
        val defaults = listOf(
            Product(nameBn = "আলু (ডায়মন্ড)", nameEn = "Diamond Potato", iconKey = "POTATO", defaultUnit = "কেজি", defaultRate = 30.0),
            Product(nameBn = "দেশি পেঁয়াজ", nameEn = "Deshi Onion", iconKey = "ONION", defaultUnit = "কেজি", defaultRate = 65.0),
            Product(nameBn = "বেগুন (গোল)", nameEn = "Round Eggplant", iconKey = "EGGPLANT", defaultUnit = "কেজি", defaultRate = 45.0),
            Product(nameBn = "কাঁচা মরিচ", nameEn = "Green Chili", iconKey = "CHILI", defaultUnit = "কেজি", defaultRate = 80.0),
            Product(nameBn = "পাকা টমেটো", nameEn = "Ripe Tomato", iconKey = "TOMATO", defaultUnit = "কেজি", defaultRate = 50.0),
            Product(nameBn = "ফুলকপি", nameEn = "Cauliflower", iconKey = "CAULIFLOWER", defaultUnit = "কেজি", defaultRate = 35.0),
            Product(nameBn = "বাঁধাকপি", nameEn = "Cabbage", iconKey = "CABBAGE", defaultUnit = "কেজি", defaultRate = 28.0),
            Product(nameBn = "রসুন", nameEn = "Garlic", iconKey = "GARLIC", defaultUnit = "কেজি", defaultRate = 180.0),
            Product(nameBn = "আদা", nameEn = "Ginger", iconKey = "GINGER", defaultUnit = "কেজি", defaultRate = 190.0),
            Product(nameBn = "মিষ্টি কুমড়া", nameEn = "Sweet Pumpkin", iconKey = "PUMPKIN", defaultUnit = "কেজি", defaultRate = 32.0)
        )
        defaults.forEach { addProduct(it) }
    }
}
