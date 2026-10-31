package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BusinessSettings
import com.example.data.model.DraftInvoiceLine
import com.example.data.model.Expense
import com.example.data.model.Invoice
import com.example.data.model.InvoiceCalculator
import com.example.data.model.ReportCalculator
import com.example.data.repository.WholesaleRepository
import com.example.util.FormatUtils
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and provisioned firestore_database_id from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("Khandaker Veg Invoice", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `multi-rate same vegetable lines remain separate and calculate 7 percent commission accurately`() {
        val lines = listOf(
            DraftInvoiceLine(localLineId = "1", productName = "আলু", quantity = 50.0, unit = "কেজি", rate = 30.0),
            DraftInvoiceLine(localLineId = "2", productName = "আলু", quantity = 20.0, unit = "কেজি", rate = 32.0)
        )
        assertEquals(1500.0, lines[0].lineTotal, 0.001)
        assertEquals(640.0, lines[1].lineTotal, 0.001)

        val totalsWithComm = InvoiceCalculator.calculateTotals(
            lines = lines,
            commissionEnabled = true,
            commissionRate = 7.0,
            additionalCharges = 50.0,
            discount = 10.0
        )
        assertEquals(2, totalsWithComm.itemCount)
        assertEquals(70.0, totalsWithComm.totalQuantity, 0.001)
        assertEquals(2140.0, totalsWithComm.productSubtotal, 0.001)
        assertEquals(149.80, totalsWithComm.commissionAmount, 0.001)
        assertEquals(2329.80, totalsWithComm.finalAmount, 0.001)

        val totalsNoComm = InvoiceCalculator.calculateTotals(
            lines = lines,
            commissionEnabled = false,
            commissionRate = 7.0,
            additionalCharges = 0.0,
            discount = 0.0
        )
        assertEquals(0.0, totalsNoComm.commissionAmount, 0.001)
        assertEquals(2140.0, totalsNoComm.finalAmount, 0.001)
    }

    @Test
    fun `report calculator applies exact net business formula and bengali numeral parsing`() {
        assertEquals(32.5, FormatUtils.parseFlexibleDouble("৩২.৫") ?: 0.0, 0.001)
        assertEquals("৳১,২৫,০০০", FormatUtils.formatCurrency(125000.0, "৳", useBengaliNumerals = true))

        val invoices = listOf(
            Invoice(
                id = "inv1",
                productSubtotal = 10000.0,
                commissionEnabled = true,
                commissionRate = 7.0,
                commissionAmount = 700.0,
                additionalCharges = 120.0,
                discount = 20.0,
                finalAmount = 10800.0
            )
        )
        val expenses = listOf(
            Expense(id = "exp1", category = "LABOR", description = "লেবার", amount = 300.0)
        )
        val report = ReportCalculator.calculateSummary(invoices, expenses)
        assertEquals(10000.0, report.totalProductSales, 0.001)
        assertEquals(700.0, report.totalCommission, 0.001)
        assertEquals(300.0, report.totalManualExpenses, 0.001)
        // Net Business Amount = 10000 - 700 - 300 = 9000 (additional charges & discounts reconciled separately)
        assertEquals(9000.0, report.netBusinessAmount, 0.001)
        assertEquals(120.0, report.totalAdditionalCharges, 0.001)
        assertEquals(20.0, report.totalDiscounts, 0.001)
    }

    @Test
    fun `invoice sequence generator produces unique sequential chalan numbers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(
                context,
                com.google.firebase.FirebaseOptions.Builder()
                    .setProjectId("gen-lang-client-0762605394")
                    .setApplicationId("1:982890725444:android:1522e1884ad891f2847c11")
                    .setApiKey("fake-test-api-key")
                    .build()
            )
        }
        val repo = WholesaleRepository(context)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val settings = BusinessSettings(invoicePrefix = "KVT", nextInvoiceSeq = 1L)

        val firstNum = repo.generateNextInvoiceNumber(settings, emptyList())
        assertEquals("KVT-$year-0001", firstNum)

        val existing = listOf(Invoice(invoiceNumber = "KVT-$year-0005"))
        val nextNum = repo.generateNextInvoiceNumber(settings, existing)
        assertEquals("KVT-$year-0006", nextNum)
    }
}
