package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToLong

@IgnoreExtraProperties
data class BusinessSettings(
    @DocumentId val id: String = "business_config",
    val userId: String = "",
    val businessName: String = "মেসার্স কর্ণফুলী ট্রেডার্স",
    val businessAddress: String = "রিয়াজউদ্দিন বাজার, চট্টগ্রাম",
    val businessPhone: String = "",
    val currencySymbol: String = "৳",
    val currencyCode: String = "BDT",
    val defaultCommissionRate: Double = 7.0,
    val commissionEnabled: Boolean = true,
    val invoicePrefix: String = "KVT",
    val nextInvoiceSeq: Long = 1L,
    val defaultPrintFormat: String = "A4", // "A4" or "THERMAL_80MM"
    val logoPreset: String = "BASKET", // "BASKET", "LEAF", "SCALE", "CUSTOM"
    val dateFormatPattern: String = "dd/MM/yyyy", // "dd/MM/yyyy", "dd MMM yyyy", "yyyy-MM-dd"
    val useBengaliNumerals: Boolean = true,
    val languagePref: String = "BN", // "BN" or "BN_EN"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nameBn: String = "",
    val nameEn: String = "",
    val iconKey: String = "POTATO",
    val defaultUnit: String = "কেজি",
    val defaultRate: Double = 0.0,
    val notes: String = "",
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "নগদ ক্রেতা",
    val customerPhone: String = "",
    val deliveryAddress: String = "",
    val invoiceDateMillis: Long = 0L,
    val itemCount: Long = 1L,
    val totalQuantity: Double = 0.0,
    val productSubtotal: Double = 0.0,
    val commissionEnabled: Boolean = true,
    val commissionRate: Double = 7.0,
    val commissionAmount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val discount: Double = 0.0,
    val finalAmount: Double = 0.0,
    val notes: String = "",
    val status: String = "SAVED", // "SAVED", "PAID", "DUE"
    val businessNameSnapshot: String = "মেসার্স কর্ণফুলী ট্রেডার্স",
    val businessAddressSnapshot: String = "",
    val businessPhoneSnapshot: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InvoiceItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceId: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unit: String = "কেজি",
    val rate: Double = 0.0,
    val lineTotal: Double = 0.0,
    val sortOrder: Long = 0L,
    val invoiceDateMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val expenseDateMillis: Long = 0L,
    val category: String = "LABOR", // TRANSPORT, LABOR, LOADING, MARKET, PACKAGING, OTHER
    val description: String = "",
    val amount: Double = 0.0,
    val relatedInvoiceId: String = "",
    val relatedInvoiceNumber: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Draft line item used in the Create / Edit Invoice screen.
 * Each line has a unique localLineId so two lines with the exact same product name
 * (e.g., আলু @ ৳30 and আলু @ ৳32) are NEVER merged.
 */
data class DraftInvoiceLine(
    val localLineId: String,
    val existingDocId: String = "",
    val existingCreatedAt: Timestamp? = null,
    val productId: String = "",
    val productName: String,
    val quantity: Double,
    val unit: String = "কেজি",
    val rate: Double
) {
    val lineTotal: Double
        get() = InvoiceCalculator.calculateLineTotal(quantity, rate)
}

data class InvoiceTotalsSummary(
    val itemCount: Int,
    val totalQuantity: Double,
    val productSubtotal: Double,
    val commissionEnabled: Boolean,
    val commissionRate: Double,
    val commissionAmount: Double,
    val additionalCharges: Double,
    val discount: Double,
    val finalAmount: Double
)

data class BusinessReportSummary(
    val invoiceCount: Int,
    val totalQuantity: Double,
    val totalProductSales: Double,
    val totalCommission: Double,
    val totalManualExpenses: Double,
    val totalAdditionalCharges: Double,
    val totalDiscounts: Double,
    val totalFinalInvoiceAmount: Double,
    val netBusinessAmount: Double
)

enum class VegetableIcon(val key: String, val labelBn: String, val labelEn: String, val emojiBadge: String) {
    POTATO("POTATO", "আলু", "Potato", "🥔"),
    ONION("ONION", "পেঁয়াজ", "Onion", "🧅"),
    EGGPLANT("EGGPLANT", "বেগুন", "Eggplant", "🍆"),
    CHILI("CHILI", "কাঁচা মরিচ", "Green Chili", "🌶️"),
    TOMATO("TOMATO", "টমেটো", "Tomato", "🍅"),
    CABBAGE("CABBAGE", "বাঁধাকপি", "Cabbage", "🥬"),
    CAULIFLOWER("CAULIFLOWER", "ফুলকপি", "Cauliflower", "🥦"),
    CARROT("CARROT", "গাজর", "Carrot", "🥕"),
    GARLIC("GARLIC", "রসুন", "Garlic", "🧄"),
    GINGER("GINGER", "আদা", "Ginger", "🫚"),
    CUCUMBER("CUCUMBER", "শসা", "Cucumber", "🥒"),
    PUMPKIN("PUMPKIN", "মিষ্টি কুমড়া", "Pumpkin", "🎃"),
    GOURD("GOURD", "লাউ / পটল", "Gourd", "🍈"),
    OKRA("OKRA", "ঢেঁড়স", "Okra", "🌿"),
    BEANS("BEANS", "শিম / বরবটি", "Beans", "🫛"),
    LEAFY("LEAFY", "শাকসবজি", "Leafy Veg", "🥬");

    companion object {
        fun fromKey(key: String): VegetableIcon = entries.find { it.key == key } ?: POTATO
    }
}

enum class ExpenseCategory(val key: String, val labelBn: String, val labelEn: String) {
    TRANSPORT("TRANSPORT", "পরিবহন / গাড়ি ভাড়া", "Transport"),
    LABOR("LABOR", "লেবার / খামালি", "Labor / Khamali"),
    LOADING("LOADING", "লোডিং ও আনলোডিং", "Loading & Unloading"),
    MARKET("MARKET", "বাজার ও আড়ত খরচ", "Market Expenses"),
    PACKAGING("PACKAGING", "বস্তা ও প্যাকেজিং", "Packaging"),
    OTHER("OTHER", "অন্যান্য খরচ", "Other Expenses");

    companion object {
        fun fromKey(key: String): ExpenseCategory = entries.find { it.key == key } ?: OTHER
    }
}

enum class InvoiceStatus(val key: String, val labelBn: String) {
    SAVED("SAVED", "সংরক্ষিত"),
    PAID("PAID", "পরিশোধিত"),
    DUE("DUE", "বাকি");

    companion object {
        fun fromKey(key: String): InvoiceStatus = entries.find { it.key == key } ?: SAVED
    }
}

object InvoiceCalculator {
    fun round2(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    fun calculateLineTotal(quantity: Double, rate: Double): Double {
        if (quantity <= 0.0 || rate < 0.0) return 0.0
        return round2(quantity * rate)
    }

    fun calculateCommission(
        productSubtotal: Double,
        commissionEnabled: Boolean,
        commissionRate: Double
    ): Double {
        if (!commissionEnabled || productSubtotal <= 0.0 || commissionRate <= 0.0) return 0.0
        return round2((productSubtotal * commissionRate) / 100.0)
    }

    /**
     * Exact formula per specification:
     * Final Invoice Amount = Product Subtotal + Commission + Additional Charges − Discount
     */
    fun calculateTotals(
        lines: List<DraftInvoiceLine>,
        commissionEnabled: Boolean,
        commissionRate: Double,
        additionalCharges: Double,
        discount: Double
    ): InvoiceTotalsSummary {
        val totalQty = round2(lines.sumOf { it.quantity.coerceAtLeast(0.0) })
        val subtotal = round2(lines.sumOf { it.lineTotal })
        val safeRate = commissionRate.coerceIn(0.0, 100.0)
        val commissionAmt = calculateCommission(subtotal, commissionEnabled, safeRate)
        val safeAdditional = additionalCharges.coerceAtLeast(0.0)
        val safeDiscount = discount.coerceAtLeast(0.0)
        val rawFinal = subtotal + commissionAmt + safeAdditional - safeDiscount
        val finalAmt = round2(rawFinal.coerceAtLeast(0.0))

        return InvoiceTotalsSummary(
            itemCount = lines.size,
            totalQuantity = totalQty,
            productSubtotal = subtotal,
            commissionEnabled = commissionEnabled,
            commissionRate = safeRate,
            commissionAmount = commissionAmt,
            additionalCharges = round2(safeAdditional),
            discount = round2(safeDiscount),
            finalAmount = finalAmt
        )
    }
}

object ReportCalculator {
    /**
     * Exact formula per specification:
     * Net Business Amount = Product Sales Subtotal − Recorded Commission − Recorded Business Expenses
     * Additional charges and discounts are shown separately for reconciliation.
     */
    fun calculateSummary(
        invoices: List<Invoice>,
        expenses: List<Expense>
    ): BusinessReportSummary {
        val totalQty = InvoiceCalculator.round2(invoices.sumOf { it.totalQuantity })
        val totalSales = InvoiceCalculator.round2(invoices.sumOf { it.productSubtotal })
        val totalComm = InvoiceCalculator.round2(invoices.sumOf { if (it.commissionEnabled) it.commissionAmount else 0.0 })
        val totalExp = InvoiceCalculator.round2(expenses.sumOf { it.amount })
        val totalAdd = InvoiceCalculator.round2(invoices.sumOf { it.additionalCharges })
        val totalDisc = InvoiceCalculator.round2(invoices.sumOf { it.discount })
        val totalFinal = InvoiceCalculator.round2(invoices.sumOf { it.finalAmount })
        val netBusiness = InvoiceCalculator.round2(totalSales - totalComm - totalExp)

        return BusinessReportSummary(
            invoiceCount = invoices.size,
            totalQuantity = totalQty,
            totalProductSales = totalSales,
            totalCommission = totalComm,
            totalManualExpenses = totalExp,
            totalAdditionalCharges = totalAdd,
            totalDiscounts = totalDisc,
            totalFinalInvoiceAmount = totalFinal,
            netBusinessAmount = netBusiness
        )
    }
}
