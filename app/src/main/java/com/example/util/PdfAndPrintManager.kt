package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.BusinessReportSummary
import com.example.data.model.BusinessSettings
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfAndPrintManager {

    private const val COLOR_BRAND_GREEN = "#146C3B"
    private const val COLOR_LIGHT_GREEN = "#ECFDF3"
    private const val COLOR_DARK_TEXT = "#111827"
    private const val COLOR_MUTED_TEXT = "#4B5563"
    private const val COLOR_BORDER = "#D1D5DB"

    private fun loadBengaliTypeface(context: Context, bold: Boolean = false): Typeface {
        val base = try {
            ResourcesCompat.getFont(context, R.font.hind_siliguri) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /**
     * Generates an Invoice / Chalan PDF in either Standard A4 ("A4") or 80mm Thermal ("THERMAL_80MM") layout.
     */
    fun generateInvoicePdfFile(
        context: Context,
        invoice: Invoice,
        items: List<InvoiceItem>,
        settings: BusinessSettings,
        formatOverride: String? = null
    ): File {
        val printFormat = formatOverride ?: settings.defaultPrintFormat
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val safeNum = invoice.invoiceNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val suffix = if (printFormat == "THERMAL_80MM") "80mm" else "A4"
        val outFile = File(dir, "Chalan_${safeNum}_$suffix.pdf")

        val pdfDocument = PdfDocument()
        try {
            if (printFormat == "THERMAL_80MM") {
                drawThermal80mmInvoice(context, pdfDocument, invoice, items, settings)
            } else {
                drawStandardA4Invoice(context, pdfDocument, invoice, items, settings)
            }
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
        return outFile
    }

    /**
     * Generates a Printable Report PDF (Daily, Monthly, or Custom Date Range).
     */
    fun generateReportPdfFile(
        context: Context,
        reportTitle: String,
        periodLabel: String,
        summary: BusinessReportSummary,
        invoices: List<Invoice>,
        expenses: List<Expense>,
        settings: BusinessSettings
    ): File {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val outFile = File(dir, "Khandaker_Report_${System.currentTimeMillis()}.pdf")

        val pdfDocument = PdfDocument()
        try {
            drawReportPages(context, pdfDocument, reportTitle, periodLabel, summary, invoices, expenses, settings)
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
        return outFile
    }

    fun copyPdfToUri(context: Context, pdfFile: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            FileInputStream(pdfFile).use { input ->
                input.copyTo(out)
            }
        } ?: error("ফাইল সংরক্ষণের অনুমতি পাওয়া যায়নি")
    }

    fun sharePdfFile(context: Context, pdfFile: File, shareTitle: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, pdfFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, shareTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun printPdfFile(
        context: Context,
        pdfFile: File,
        jobName: String,
        isThermal80mm: Boolean = false
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("প্রিন্ট সার্ভিস পাওয়া যায়নি")

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("${jobName}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("গন্তব্য ফাইল পাওয়া যায়নি")
                    return
                }
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage ?: "প্রিন্ট করতে সমস্যা হয়েছে")
                }
            }
        }

        val attrsBuilder = PrintAttributes.Builder()
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        if (isThermal80mm) {
            attrsBuilder.setMediaSize(
                PrintAttributes.MediaSize("ROLL_80MM", "80mm Thermal Roll", 3150, 11000)
            )
        } else {
            attrsBuilder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        }
        printManager.print(jobName, adapter, attrsBuilder.build())
    }

    // -------------------- A4 INVOICE DRAWING --------------------

    private fun drawStandardA4Invoice(
        context: Context,
        pdfDocument: PdfDocument,
        invoice: Invoice,
        items: List<InvoiceItem>,
        settings: BusinessSettings
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)

        val tfRegular = loadBengaliTypeface(context, bold = false)
        val tfBold = loadBengaliTypeface(context, bold = true)

        val useBn = settings.useBengaliNumerals
        val currency = settings.currencySymbol.ifBlank { "৳" }

        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_BRAND_GREEN) }
        val lightGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_LIGHT_GREEN) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_BORDER)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 20f
            color = Color.parseColor(COLOR_BRAND_GREEN)
        }
        val subHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfRegular
            textSize = 10.5f
            color = Color.parseColor(COLOR_MUTED_TEXT)
        }
        val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 11f
            color = Color.parseColor(COLOR_DARK_TEXT)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfRegular
            textSize = 10.5f
            color = Color.parseColor(COLOR_DARK_TEXT)
        }
        val whiteBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 10.5f
            color = Color.WHITE
        }
        val grandTotalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 14.5f
            color = Color.parseColor(COLOR_BRAND_GREEN)
        }

        // Paginate items cleanly (~18 items on first page, ~25 on continuation pages)
        val chunks = if (items.isEmpty()) listOf(emptyList()) else items.chunked(18)

        chunks.forEachIndexed { pageIndex, pageItems ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Clean white page background
            canvas.drawColor(Color.WHITE)

            // Top green accent bar
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 8f, greenPaint)

            var y = 38f

            // Optional custom business logo if saved locally
            val customLogoFile = File(context.filesDir, "business_logo.png")
            var textStartX = margin
            if (settings.logoPreset == "CUSTOM" && customLogoFile.exists()) {
                val bmp = BitmapFactory.decodeFile(customLogoFile.absolutePath)
                if (bmp != null) {
                    val dst = RectF(margin, y - 14f, margin + 40f, y + 26f)
                    canvas.drawBitmap(bmp, null, dst, null)
                    textStartX = margin + 48f
                }
            }

            val bizName = invoice.businessNameSnapshot.ifBlank { settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" } }
            canvas.drawText(ellipsize(bizName, titlePaint, 340f), textStartX, y + 4f, titlePaint)

            // Right badge: চালান / ইনভয়েস
            val badgeRect = RectF(pageWidth - margin - 120f, y - 14f, pageWidth - margin, y + 12f)
            canvas.drawRoundRect(badgeRect, 6f, 6f, lightGreenPaint)
            canvas.drawRoundRect(badgeRect, 6f, 6f, borderPaint)
            val badgeText = "পাইকারি চালান"
            val badgeTw = boldBodyPaint.measureText(badgeText)
            canvas.drawText(badgeText, badgeRect.centerX() - badgeTw / 2f, y + 3f, boldBodyPaint)

            y += 20f
            val bizAddr = invoice.businessAddressSnapshot.ifBlank { settings.businessAddress }
            val bizPhone = invoice.businessPhoneSnapshot.ifBlank { settings.businessPhone }
            if (bizAddr.isNotBlank()) {
                canvas.drawText(ellipsize(bizAddr, subHeaderPaint, 360f), textStartX, y, subHeaderPaint)
                y += 14f
            }
            if (bizPhone.isNotBlank()) {
                canvas.drawText("মোবাইল: $bizPhone", textStartX, y, subHeaderPaint)
                y += 14f
            }

            y += 6f
            canvas.drawLine(margin, y, pageWidth - margin, y, borderPaint)
            y += 14f

            // Customer & Chalan Meta Box
            val metaBoxTop = y
            val metaBoxHeight = 64f
            val metaRect = RectF(margin, metaBoxTop, pageWidth - margin, metaBoxTop + metaBoxHeight)
            canvas.drawRoundRect(metaRect, 6f, 6f, lightGreenPaint)
            canvas.drawRoundRect(metaRect, 6f, 6f, borderPaint)

            val leftColX = margin + 10f
            val rightColX = margin + contentWidth * 0.55f

            canvas.drawText(
                ellipsize("নাম: ${invoice.customerName}", boldBodyPaint, contentWidth * 0.50f),
                leftColX,
                metaBoxTop + 18f,
                boldBodyPaint
            )
            if (invoice.customerPhone.isNotBlank()) {
                canvas.drawText("মোবাইল: ${invoice.customerPhone}", leftColX, metaBoxTop + 36f, bodyPaint)
            }
            if (invoice.deliveryAddress.isNotBlank()) {
                canvas.drawText(
                    ellipsize("ঠিকানা: ${invoice.deliveryAddress}", bodyPaint, contentWidth * 0.50f),
                    leftColX,
                    metaBoxTop + 53f,
                    bodyPaint
                )
            }

            canvas.drawText(
                ellipsize("চালান নং: ${invoice.invoiceNumber}", boldBodyPaint, contentWidth * 0.42f),
                rightColX,
                metaBoxTop + 18f,
                boldBodyPaint
            )
            val dateStr = FormatUtils.formatDateTime(invoice.invoiceDateMillis, settings.dateFormatPattern, useBn)
            canvas.drawText("তারিখ: $dateStr", rightColX, metaBoxTop + 36f, bodyPaint)
            if (chunks.size > 1) {
                canvas.drawText("পৃষ্ঠা: ${pageIndex + 1}/${chunks.size}", rightColX, metaBoxTop + 53f, subHeaderPaint)
            }

            y = metaBoxTop + metaBoxHeight + 14f

            // Product Table Header
            val colSlX = margin + 8f
            val colNameX = margin + 40f
            val colQtyRightX = margin + contentWidth * 0.58f
            val colRateRightX = margin + contentWidth * 0.77f
            val colTotalRightX = pageWidth - margin - 10f

            val headerRect = RectF(margin, y, pageWidth - margin, y + 24f)
            canvas.drawRect(headerRect, greenPaint)
            canvas.drawText("ক্রঃ", colSlX, y + 16f, whiteBoldPaint)
            canvas.drawText("পণ্য", colNameX, y + 16f, whiteBoldPaint)
            drawRightAligned(canvas, "পরিমাণ", colQtyRightX, y + 16f, whiteBoldPaint)
            drawRightAligned(canvas, "দর ($currency)", colRateRightX, y + 16f, whiteBoldPaint)
            drawRightAligned(canvas, "মোট ($currency)", colTotalRightX, y + 16f, whiteBoldPaint)
            y += 24f

            val baseIndex = pageIndex * 18
            pageItems.forEachIndexed { idx, item ->
                val rowHeight = 22f
                if (idx % 2 == 1) {
                    val zebra = Paint().apply { color = Color.parseColor("#F9FAFB") }
                    canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, zebra)
                }
                canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, borderPaint)

                val slStr = FormatUtils.formatNumber((baseIndex + idx + 1).toDouble(), useBn, 0)
                val qtyStr = FormatUtils.formatQuantity(item.quantity, item.unit, useBn)
                val rateStr = FormatUtils.formatCurrency(item.rate, currency, useBn)
                val totalStr = FormatUtils.formatCurrency(item.lineTotal, currency, useBn)

                canvas.drawText(slStr, colSlX, y + 15f, bodyPaint)
                canvas.drawText(ellipsize(item.productName, bodyPaint, 185f), colNameX, y + 15f, bodyPaint)
                drawRightAligned(canvas, qtyStr, colQtyRightX, y + 15f, bodyPaint)
                drawRightAligned(canvas, rateStr, colRateRightX, y + 15f, bodyPaint)
                drawRightAligned(canvas, totalStr, colTotalRightX, y + 15f, boldBodyPaint)

                y += rowHeight
            }

            // Draw Totals & Signature on the final page
            if (pageIndex == chunks.lastIndex) {
                y += 14f
                val summaryLeftX = margin + contentWidth * 0.46f
                val summaryRightX = pageWidth - margin - 10f

                // Notes on the left side
                if (invoice.notes.isNotBlank()) {
                    canvas.drawText("নোট / মন্তব্য:", margin, y + 12f, boldBodyPaint)
                    canvas.drawText(
                        ellipsize(invoice.notes, subHeaderPaint, contentWidth * 0.42f),
                        margin,
                        y + 28f,
                        subHeaderPaint
                    )
                }

                // Financial Summary Rows on the right
                fun drawSummaryRow(label: String, value: String, bold: Boolean = false) {
                    val p = if (bold) boldBodyPaint else bodyPaint
                    canvas.drawText(label, summaryLeftX, y + 12f, p)
                    drawRightAligned(canvas, value, summaryRightX, y + 12f, p)
                    y += 18f
                }

                drawSummaryRow(
                    "মোট পরিমাণ:",
                    FormatUtils.formatQuantity(invoice.totalQuantity, "কেজি", useBn)
                )
                drawSummaryRow(
                    "পণ্যের মোট মূল্য:",
                    FormatUtils.formatCurrency(invoice.productSubtotal, currency, useBn),
                    bold = true
                )
                if (invoice.commissionEnabled) {
                    val rateTxt = FormatUtils.formatNumber(invoice.commissionRate, useBn, 2)
                    drawSummaryRow(
                        "কমিশন ($rateTxt%):",
                        FormatUtils.formatCurrency(invoice.commissionAmount, currency, useBn)
                    )
                }
                if (invoice.additionalCharges > 0.0) {
                    drawSummaryRow(
                        "অতিরিক্ত চার্জ:",
                        FormatUtils.formatCurrency(invoice.additionalCharges, currency, useBn)
                    )
                }
                if (invoice.discount > 0.0) {
                    drawSummaryRow(
                        "ছাড় (Discount):",
                        "-${FormatUtils.formatCurrency(invoice.discount, currency, useBn)}"
                    )
                }

                y += 4f
                val totalBox = RectF(summaryLeftX - 8f, y - 4f, pageWidth - margin, y + 24f)
                canvas.drawRoundRect(totalBox, 6f, 6f, lightGreenPaint)
                canvas.drawRoundRect(totalBox, 6f, 6f, borderPaint)
                canvas.drawText("সর্বমোট প্রদেয়:", summaryLeftX, y + 15f, grandTotalPaint)
                drawRightAligned(
                    canvas,
                    FormatUtils.formatCurrency(invoice.finalAmount, currency, useBn),
                    summaryRightX,
                    y + 15f,
                    grandTotalPaint
                )

                // Signature Area at bottom
                val sigY = (pageHeight - 54).toFloat().coerceAtLeast(y + 62f)
                if (sigY < pageHeight - 24f) {
                    canvas.drawLine(margin, sigY, margin + 140f, sigY, borderPaint)
                    canvas.drawText("ক্রেতার স্বাক্ষর", margin + 32f, sigY + 15f, subHeaderPaint)

                    canvas.drawLine(pageWidth - margin - 150f, sigY, pageWidth - margin, sigY, borderPaint)
                    canvas.drawText("বিক্রেতার স্বাক্ষর ($bizName)", pageWidth - margin - 148f, sigY + 15f, subHeaderPaint)
                }
            }

            pdfDocument.finishPage(page)
        }
    }

    // -------------------- 80MM THERMAL RECEIPT DRAWING --------------------

    private fun drawThermal80mmInvoice(
        context: Context,
        pdfDocument: PdfDocument,
        invoice: Invoice,
        items: List<InvoiceItem>,
        settings: BusinessSettings
    ) {
        val pageWidth = 226 // 80mm at 72 DPI (~226pt)
        val estimatedHeight = (310 + items.size * 26).coerceAtLeast(380)
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val tfRegular = loadBengaliTypeface(context, bold = false)
        val tfBold = loadBengaliTypeface(context, bold = true)
        val useBn = settings.useBengaliNumerals
        val currency = settings.currencySymbol.ifBlank { "৳" }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 12.5f
            color = Color.BLACK
        }
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 8.5f
            color = Color.BLACK
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfRegular
            textSize = 8.5f
            color = Color.BLACK
        }
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            strokeWidth = 0.8f
        }

        val pad = 10f
        var y = 20f

        val bizName = invoice.businessNameSnapshot.ifBlank { settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" } }
        drawCentered(canvas, ellipsize(bizName, titlePaint, pageWidth - 16f), pageWidth / 2f, y, titlePaint)
        y += 13f

        val bizAddr = invoice.businessAddressSnapshot.ifBlank { settings.businessAddress }
        if (bizAddr.isNotBlank()) {
            drawCentered(canvas, ellipsize(bizAddr, bodyPaint, pageWidth - 16f), pageWidth / 2f, y, bodyPaint)
            y += 11f
        }
        val bizPhone = invoice.businessPhoneSnapshot.ifBlank { settings.businessPhone }
        if (bizPhone.isNotBlank()) {
            drawCentered(canvas, "মোবাইল: $bizPhone", pageWidth / 2f, y, bodyPaint)
            y += 11f
        }

        canvas.drawLine(pad, y, pageWidth - pad, y, dashPaint)
        y += 12f

        canvas.drawText("চালান: ${invoice.invoiceNumber}", pad, y, boldPaint)
        y += 11f
        canvas.drawText(
            "তারিখ: ${FormatUtils.formatDateTime(invoice.invoiceDateMillis, settings.dateFormatPattern, useBn)}",
            pad,
            y,
            bodyPaint
        )
        y += 11f
        canvas.drawText(ellipsize("নাম: ${invoice.customerName}", boldPaint, pageWidth - 20f), pad, y, boldPaint)
        y += 11f
        if (invoice.customerPhone.isNotBlank()) {
            canvas.drawText("ফোন: ${invoice.customerPhone}", pad, y, bodyPaint)
            y += 11f
        }

        canvas.drawLine(pad, y, pageWidth - pad, y, dashPaint)
        y += 11f

        canvas.drawText("পণ্য", pad, y, boldPaint)
        drawRightAligned(canvas, "পরিমাণ×দর", pageWidth - 68f, y, boldPaint)
        drawRightAligned(canvas, "মোট", pageWidth - pad, y, boldPaint)
        y += 6f
        canvas.drawLine(pad, y, pageWidth - pad, y, dashPaint)
        y += 12f

        items.forEach { item ->
            canvas.drawText(ellipsize(item.productName, boldPaint, 95f), pad, y, boldPaint)
            val qtyRate = "${FormatUtils.formatNumber(item.quantity, useBn)}×${FormatUtils.formatNumber(item.rate, useBn)}"
            drawRightAligned(canvas, qtyRate, pageWidth - 62f, y, bodyPaint)
            drawRightAligned(
                canvas,
                FormatUtils.formatCurrency(item.lineTotal, currency, useBn),
                pageWidth - pad,
                y,
                boldPaint
            )
            y += 14f
        }

        canvas.drawLine(pad, y, pageWidth - pad, y, dashPaint)
        y += 12f

        fun thermalRow(label: String, value: String, bold: Boolean = false) {
            val p = if (bold) boldPaint else bodyPaint
            canvas.drawText(label, pad, y, p)
            drawRightAligned(canvas, value, pageWidth - pad, y, p)
            y += 12f
        }

        thermalRow("মোট ওজন:", FormatUtils.formatQuantity(invoice.totalQuantity, "কেজি", useBn))
        thermalRow("পণ্যের মোট:", FormatUtils.formatCurrency(invoice.productSubtotal, currency, useBn), bold = true)
        if (invoice.commissionEnabled) {
            thermalRow(
                "কমিশন (${FormatUtils.formatNumber(invoice.commissionRate, useBn)}%):",
                FormatUtils.formatCurrency(invoice.commissionAmount, currency, useBn)
            )
        }
        if (invoice.additionalCharges > 0.0) {
            thermalRow("অতিরিক্ত চার্জ:", FormatUtils.formatCurrency(invoice.additionalCharges, currency, useBn))
        }
        if (invoice.discount > 0.0) {
            thermalRow("ছাড়:", "-${FormatUtils.formatCurrency(invoice.discount, currency, useBn)}")
        }

        canvas.drawLine(pad, y, pageWidth - pad, y, dashPaint)
        y += 14f
        canvas.drawText("সর্বমোট:", pad, y, titlePaint)
        drawRightAligned(
            canvas,
            FormatUtils.formatCurrency(invoice.finalAmount, currency, useBn),
            pageWidth - pad,
            y,
            titlePaint
        )
        y += 18f

        if (invoice.notes.isNotBlank()) {
            canvas.drawText(ellipsize("নোট: ${invoice.notes}", bodyPaint, pageWidth - 20f), pad, y, bodyPaint)
            y += 14f
        }
        drawCentered(canvas, "ধন্যবাদ — আবার আসবেন", pageWidth / 2f, y, bodyPaint)

        pdfDocument.finishPage(page)
    }

    // -------------------- REPORT PDF DRAWING --------------------

    private fun drawReportPages(
        context: Context,
        pdfDocument: PdfDocument,
        reportTitle: String,
        periodLabel: String,
        summary: BusinessReportSummary,
        invoices: List<Invoice>,
        expenses: List<Expense>,
        settings: BusinessSettings
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        val tfRegular = loadBengaliTypeface(context, bold = false)
        val tfBold = loadBengaliTypeface(context, bold = true)
        val useBn = settings.useBengaliNumerals
        val currency = settings.currencySymbol.ifBlank { "৳" }

        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_BRAND_GREEN) }
        val lightGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_LIGHT_GREEN) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_BORDER)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 18f
            color = Color.parseColor(COLOR_BRAND_GREEN)
        }
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 10.5f
            color = Color.parseColor(COLOR_DARK_TEXT)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfRegular
            textSize = 10f
            color = Color.parseColor(COLOR_DARK_TEXT)
        }
        val whiteBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfBold
            textSize = 10f
            color = Color.WHITE
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 8f, greenPaint)

        var y = 36f
        canvas.drawText(settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" }, margin, y, titlePaint)
        y += 18f
        canvas.drawText("$reportTitle  |  সময়কাল: $periodLabel", margin, y, boldPaint)
        y += 16f

        val box = RectF(margin, y, pageWidth - margin, y + 114f)
        canvas.drawRoundRect(box, 6f, 6f, lightGreenPaint)
        canvas.drawRoundRect(box, 6f, 6f, borderPaint)

        var sy = y + 18f
        val leftX = margin + 12f
        val rightX = margin + contentWidth * 0.52f

        canvas.drawText("মোট চালান সংখ্যা: ${FormatUtils.formatNumber(summary.invoiceCount.toDouble(), useBn, 0)}", leftX, sy, bodyPaint)
        canvas.drawText("মোট পণ্যের ওজন: ${FormatUtils.formatQuantity(summary.totalQuantity, "কেজি", useBn)}", rightX, sy, bodyPaint)
        sy += 18f

        canvas.drawText("মোট পণ্য বিক্রি (Subtotal): ${FormatUtils.formatCurrency(summary.totalProductSales, currency, useBn)}", leftX, sy, boldPaint)
        canvas.drawText("মোট কমিশন: ${FormatUtils.formatCurrency(summary.totalCommission, currency, useBn)}", rightX, sy, bodyPaint)
        sy += 18f

        canvas.drawText("ম্যানুয়াল ব্যবসায়িক খরচ: ${FormatUtils.formatCurrency(summary.totalManualExpenses, currency, useBn)}", leftX, sy, bodyPaint)
        canvas.drawText("অতিরিক্ত চার্জ / ছাড়: +${FormatUtils.formatCurrency(summary.totalAdditionalCharges, currency, useBn)} / -${FormatUtils.formatCurrency(summary.totalDiscounts, currency, useBn)}", rightX, sy, bodyPaint)
        sy += 20f

        canvas.drawText(
            "নিট ব্যবসায়িক আয় (বিক্রি − কমিশন − খরচ): ${FormatUtils.formatCurrency(summary.netBusinessAmount, currency, useBn)}",
            leftX,
            sy,
            titlePaint.apply { textSize = 13.5f }
        )

        y += 130f

        // Recent Invoices Table (up to 16 rows on summary sheet)
        canvas.drawText("চালান তালিকা (${FormatUtils.formatNumber(invoices.size.toDouble(), useBn, 0)}টি):", margin, y, boldPaint)
        y += 8f

        canvas.drawRect(margin, y, pageWidth - margin, y + 20f, greenPaint)
        canvas.drawText("চালান নং", margin + 6f, y + 14f, whiteBoldPaint)
        canvas.drawText("কাস্টমার", margin + 120f, y + 14f, whiteBoldPaint)
        drawRightAligned(canvas, "পণ্য মূল", margin + 350f, y + 14f, whiteBoldPaint)
        drawRightAligned(canvas, "কমিশন", margin + 430f, y + 14f, whiteBoldPaint)
        drawRightAligned(canvas, "সর্বমোট", pageWidth - margin - 6f, y + 14f, whiteBoldPaint)
        y += 20f

        invoices.take(14).forEach { inv ->
            canvas.drawRect(margin, y, pageWidth - margin, y + 18f, borderPaint)
            canvas.drawText(ellipsize(inv.invoiceNumber, bodyPaint, 108f), margin + 6f, y + 13f, bodyPaint)
            canvas.drawText(ellipsize(inv.customerName, bodyPaint, 140f), margin + 120f, y + 13f, bodyPaint)
            drawRightAligned(canvas, FormatUtils.formatCurrency(inv.productSubtotal, currency, useBn), margin + 350f, y + 13f, bodyPaint)
            drawRightAligned(canvas, FormatUtils.formatCurrency(inv.commissionAmount, currency, useBn), margin + 430f, y + 13f, bodyPaint)
            drawRightAligned(canvas, FormatUtils.formatCurrency(inv.finalAmount, currency, useBn), pageWidth - margin - 6f, y + 13f, boldPaint)
            y += 18f
        }

        if (expenses.isNotEmpty() && y < pageHeight - 120f) {
            y += 16f
            canvas.drawText("ম্যানুয়াল খরচ তালিকা (${FormatUtils.formatNumber(expenses.size.toDouble(), useBn, 0)}টি):", margin, y, boldPaint)
            y += 8f
            expenses.take(8).forEach { exp ->
                if (y < pageHeight - 40f) {
                    canvas.drawRect(margin, y, pageWidth - margin, y + 18f, borderPaint)
                    val catName = ExpenseCategory.fromKey(exp.category).labelBn
                    canvas.drawText(ellipsize("$catName — ${exp.description}", bodyPaint, 380f), margin + 6f, y + 13f, bodyPaint)
                    drawRightAligned(canvas, FormatUtils.formatCurrency(exp.amount, currency, useBn), pageWidth - margin - 6f, y + 13f, boldPaint)
                    y += 18f
                }
            }
        }

        pdfDocument.finishPage(page)
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private fun drawRightAligned(canvas: Canvas, text: String, rightX: Float, y: Float, paint: Paint) {
        val w = paint.measureText(text)
        canvas.drawText(text, rightX - w, y, paint)
    }

    private fun drawCentered(canvas: Canvas, text: String, centerX: Float, y: Float, paint: Paint) {
        val w = paint.measureText(text)
        canvas.drawText(text, centerX - (w / 2f), y, paint)
    }
}
