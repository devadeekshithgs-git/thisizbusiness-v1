package com.kiranaflow.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object Formatters {
    /**
     * Indian grouping format (e.g., 1,23,456) with configurable fraction digits.
     */
    fun formatInrNumber(value: Double, fractionDigits: Int = 0): String {
        val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
        nf.maximumFractionDigits = fractionDigits.coerceAtLeast(0)
        nf.minimumFractionDigits = fractionDigits.coerceAtLeast(0)
        return nf.format(value)
    }

    /**
     * INR currency display with ₹ symbol. By default uses absolute value for consistency with existing UI
     * that indicates profit/loss via labels/colors rather than sign.
     */
    fun formatInrCurrency(value: Double, fractionDigits: Int = 0, useAbsolute: Boolean = true): String {
        val v = if (useAbsolute) abs(value) else value
        return "₹${formatInrNumber(v, fractionDigits)}"
    }

    /**
     * Masks digits for privacy overlay while preserving non-digit characters like ₹, commas, and dots.
     */
    fun maskDigits(text: String, maskChar: Char = '•'): String =
        text.map { ch -> if (ch.isDigit()) maskChar else ch }.joinToString("")
}


