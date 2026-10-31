package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FormatUtils {
    private val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun toBengaliDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(bnDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Normalizes user-typed numeric strings supporting BOTH Bengali digits (০-৯) and English digits (0-9).
     */
    fun parseFlexibleDouble(input: String): Double? {
        if (input.isBlank()) return null
        val sb = StringBuilder()
        for (ch in input.trim()) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch in '০'..'৯' -> sb.append(('0'.code + (ch - '০')).toChar())
                ch == '.' -> sb.append('.')
                ch == '-' -> sb.append('-')
                ch == ',' || ch == ' ' || ch == '৳' -> { /* skip grouping/currency */ }
                else -> return null
            }
        }
        return sb.toString().toDoubleOrNull()
    }

    /**
     * Formats numbers using Bangladeshi/South Asian grouping (e.g., 1,25,000 or ১,২৫,০০০).
     */
    fun formatNumber(
        value: Double,
        useBengaliNumerals: Boolean = true,
        maxDecimals: Int = 2
    ): String {
        if (value.isNaN() || value.isInfinite()) {
            return if (useBengaliNumerals) "০" else "0"
        }
        val isNegative = value < -0.0001
        val absVal = abs(value)
        val formattedRaw = String.format(Locale.US, "%.${maxDecimals}f", absVal)
        val parts = formattedRaw.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1].trimEnd('0') else ""

        val groupedInt = groupBangladeshiInteger(intPart)
        val combined = buildString {
            if (isNegative) append("-")
            append(groupedInt)
            if (decPart.isNotEmpty()) {
                append(".")
                append(decPart)
            }
        }
        return if (useBengaliNumerals) toBengaliDigits(combined) else combined
    }

    fun formatCurrency(
        value: Double,
        currencySymbol: String = "৳",
        useBengaliNumerals: Boolean = true
    ): String {
        val isNegative = value < -0.0001
        val absFormatted = formatNumber(abs(value), useBengaliNumerals = useBengaliNumerals, maxDecimals = 2)
        return if (isNegative) "-$currencySymbol$absFormatted" else "$currencySymbol$absFormatted"
    }

    fun formatQuantity(
        quantity: Double,
        unit: String = "কেজি",
        useBengaliNumerals: Boolean = true
    ): String {
        val numStr = formatNumber(quantity, useBengaliNumerals = useBengaliNumerals, maxDecimals = 2)
        return "$numStr $unit"
    }

    private fun groupBangladeshiInteger(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        var remaining = digits.dropLast(3)
        val groups = mutableListOf<String>()
        while (remaining.length > 2) {
            groups.add(0, remaining.takeLast(2))
            remaining = remaining.dropLast(2)
        }
        if (remaining.isNotEmpty()) {
            groups.add(0, remaining)
        }
        return groups.joinToString(",") + "," + lastThree
    }

    fun formatDate(
        epochMillis: Long,
        pattern: String = "dd/MM/yyyy",
        useBengaliNumerals: Boolean = true
    ): String {
        if (epochMillis <= 0L) return ""
        val safePattern = pattern.ifBlank { "dd/MM/yyyy" }
        val sdf = SimpleDateFormat(safePattern, Locale.US)
        val raw = sdf.format(Date(epochMillis))
        return if (useBengaliNumerals) toBengaliDigits(raw) else raw
    }

    fun formatDateTime(
        epochMillis: Long,
        datePattern: String = "dd/MM/yyyy",
        useBengaliNumerals: Boolean = true
    ): String {
        if (epochMillis <= 0L) return ""
        val safePattern = "${datePattern.ifBlank { "dd/MM/yyyy" }} hh:mm a"
        val sdf = SimpleDateFormat(safePattern, Locale.US)
        val raw = sdf.format(Date(epochMillis))
        return if (useBengaliNumerals) toBengaliDigits(raw) else raw
    }

    fun startOfDayMillis(epochMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDayMillis(epochMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun startOfMonthMillis(year: Int, monthZeroBased: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthZeroBased)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfMonthMillis(year: Int, monthZeroBased: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthZeroBased)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    val bengaliMonths = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )
}
