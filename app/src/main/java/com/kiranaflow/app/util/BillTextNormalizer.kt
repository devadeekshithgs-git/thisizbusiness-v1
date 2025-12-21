package com.kiranaflow.app.util

/**
 * Normalizes noisy OCR text from Indian vendor bills.
 *
 * Goals:
 * - reduce layout noise (extra whitespace, weird currency symbols)
 * - fix common OCR confusions in numeric contexts
 * - keep the original meaning intact as much as possible
 */
object BillTextNormalizer {
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        // Normalize line endings and remove control chars that can break parsing.
        var s = raw
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        // Normalize common currency tokens to a consistent representation.
        // Keep ₹ for display, but remove it in numeric parsing later when needed.
        s = s.replace("Rs.", "₹", ignoreCase = true)
            .replace("Rs ", "₹", ignoreCase = true)
            .replace("INR", "₹", ignoreCase = true)
            .replace("₹₹", "₹")

        // Normalize separators that often show up as table borders.
        s = s.replace("|", " ")
            .replace("—", "-")
            .replace("–", "-")

        // Collapse excessive whitespace per-line (but preserve line breaks).
        s = s.lines()
            .joinToString("\n") { line ->
                line
                    .trim()
                    .replace(Regex("\\s+"), " ")
            }
            .trim()

        return s
    }
}



