package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BusinessSettings
import com.example.data.model.Customer
import com.example.data.model.DraftInvoiceLine
import com.example.data.model.Expense
import com.example.data.model.Invoice
import com.example.data.model.InvoiceCalculator
import com.example.data.model.Product
import com.example.data.model.ReportCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WholesaleRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun fullWholesaleWorkflow_productsCustomersInvoicesMultiRateCommissionEditAndExpenses_succeeds() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = WholesaleRepository(firestore) { auth }

        // 1. Add vegetable product
        val potatoId = withTimeout(TIMEOUT_MS) {
            repo.addProduct(
                Product(
                    nameBn = "আলু",
                    nameEn = "Potato",
                    iconKey = "POTATO",
                    defaultUnit = "কেজি",
                    defaultRate = 30.0
                )
            )
        }
        assertTrue(potatoId.isNotEmpty())

        // 2. Add customer
        val customerId = withTimeout(TIMEOUT_MS) {
            repo.addCustomer(
                Customer(
                    name = "রহিম সওদাগর",
                    phone = "01812345678",
                    address = "চাক্তাই, চট্টগ্রাম"
                )
            )
        }
        assertTrue(customerId.isNotEmpty())

        // 3. Create invoice with same product (আলু) at two different rates (৳30 and ৳32) + পেঁয়াজ
        // আলু 50 * 30 = 1500; আলু 20 * 32 = 640; পেঁয়াজ 131 * 60 = 7860 => Subtotal = 10,000!
        // Commission @ 7% = 700 => Final Amount = 10,700!
        val lines = listOf(
            DraftInvoiceLine(
                localLineId = "line_1",
                productId = potatoId,
                productName = "আলু",
                quantity = 50.0,
                unit = "কেজি",
                rate = 30.0
            ),
            DraftInvoiceLine(
                localLineId = "line_2",
                productId = potatoId,
                productName = "আলু",
                quantity = 20.0,
                unit = "কেজি",
                rate = 32.0
            ),
            DraftInvoiceLine(
                localLineId = "line_3",
                productId = "",
                productName = "পেঁয়াজ",
                quantity = 131.0,
                unit = "কেজি",
                rate = 60.0
            )
        )

        val settings = BusinessSettings(userId = aliceUid)
        val invoiceNumber = "KVT-2026-${UUID.randomUUID().toString().take(6).uppercase()}"
        val nowMillis = 1730280000000L

        val invoiceId = withTimeout(TIMEOUT_MS) {
            repo.createInvoiceWithItems(
                invoiceDraft = Invoice(
                    invoiceNumber = invoiceNumber,
                    customerId = customerId,
                    customerName = "রহিম সওদাগর",
                    customerPhone = "01812345678",
                    invoiceDateMillis = nowMillis,
                    commissionEnabled = true,
                    commissionRate = 7.0,
                    additionalCharges = 0.0,
                    discount = 0.0
                ),
                lines = lines,
                currentSettings = settings
            )
        }
        assertTrue(invoiceId.isNotEmpty())

        // Verify saved invoice and that BOTH আলু lines are preserved separately
        val savedInvoices = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeInvoices(aliceUid).first { list -> list.any { it.id == invoiceId } }
        }
        val savedInvoice = savedInvoices.first { it.id == invoiceId }
        assertEquals(201.0, savedInvoice.totalQuantity, 0.01)
        assertEquals(10000.0, savedInvoice.productSubtotal, 0.01)
        assertEquals(700.0, savedInvoice.commissionAmount, 0.01)
        assertEquals(10700.0, savedInvoice.finalAmount, 0.01)

        val savedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeInvoiceItems(aliceUid).first { list -> list.count { it.invoiceId == invoiceId } == 3 }
        }.filter { it.invoiceId == invoiceId }

        val aluItems = savedItems.filter { it.productName == "আলু" }
        assertEquals("Both আলু lines at different rates must remain separate", 2, aluItems.size)
        assertEquals(1500.0, aluItems[0].lineTotal, 0.01)
        assertEquals(640.0, aluItems[1].lineTotal, 0.01)

        // 4. Edit saved invoice: disable commission & verify new invoice total = 10,000
        val editLines = savedItems.map { item ->
            DraftInvoiceLine(
                localLineId = item.id,
                existingDocId = item.id,
                existingCreatedAt = item.createdAt,
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                unit = item.unit,
                rate = item.rate
            )
        }
        withTimeout(TIMEOUT_MS) {
            repo.updateInvoiceWithItems(
                existingInvoice = savedInvoice.copy(commissionEnabled = false),
                lines = editLines
            )
        }

        val updatedInvoice = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeInvoices(aliceUid).first { list ->
                list.any { it.id == invoiceId && !it.commissionEnabled }
            }
        }.first { it.id == invoiceId }
        assertEquals(0.0, updatedInvoice.commissionAmount, 0.01)
        assertEquals(10000.0, updatedInvoice.finalAmount, 0.01)

        // 5. Add manual expense & verify Net Business Amount calculation
        val expenseId = withTimeout(TIMEOUT_MS) {
            repo.addExpense(
                Expense(
                    expenseDateMillis = nowMillis,
                    category = "LABOR",
                    description = "খামালি খরচ",
                    amount = 450.0,
                    relatedInvoiceId = invoiceId,
                    relatedInvoiceNumber = invoiceNumber
                )
            )
        }
        assertTrue(expenseId.isNotEmpty())

        val expenses = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeExpenses(aliceUid).first { list -> list.any { it.id == expenseId } }
        }
        val report = ReportCalculator.calculateSummary(listOf(savedInvoice), expenses)
        // savedInvoice had subtotal 10,000, commission 700, manual expense 450 => Net = 10,000 - 700 - 450 = 8,850
        assertEquals(8850.0, report.netBusinessAmount, 0.01)
        assertEquals(700.0, InvoiceCalculator.calculateCommission(10000.0, true, 7.0), 0.01)
    }

    @Test
    fun crossUserIsolation_bobCannotReadAliceCustomer_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val aliceRepo = WholesaleRepository(firestore) { auth }
        val custId = withTimeout(TIMEOUT_MS) {
            aliceRepo.addCustomer(Customer(name = "গোপন কাস্টমার", phone = "01700000000"))
        }

        // Switch to Bob
        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        val bobRepo = WholesaleRepository(firestore) { auth }

        try {
            withTimeout(TIMEOUT_MS) {
                bobRepo.getCustomerById(aliceUid, custId)
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user access")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun unauthenticatedCaller_observeProducts_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_unauth_${UUID.randomUUID()}@test.com")
        auth.signOut()
        val repo = WholesaleRepository(firestore) { auth }

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeProducts(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected wrapped FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
