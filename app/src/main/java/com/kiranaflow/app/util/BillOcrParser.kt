package com.kiranaflow.app.util

/**
 * Best-effort OCR text parser for Indian vendor bills (Hindi/English mixed).
 *
 * This is intentionally heuristic: real-world bills vary a lot. We aim to extract:
 * - vendor details (best-effort)
 * - line items: name, qty, unit price, total
 */
object BillOcrParser {
    data class ParsedVendor(
        val name: String? = null,
        val gstNumber: String? = null,
        val phone: String? = null
    )

    data class ParsedBillItem(
        val name: String,
        val qty: Int,
        val unitPrice: Double? = null,
        val total: Double? = null,
        val rawLine: String
    )

    data class ParsedBill(
        val vendor: ParsedVendor,
        val items: List<ParsedBillItem>
    )

    private val skipLineRegex = Regex(
        pattern = "(?i)^(total|grand\\s*total|sub\\s*total|gst|cgst|sgst|igst|invoice|bill\\s*no|date|amount|net|round\\s*off|cash|upi|balance|thank)",
    )

    private val gstRegex = Regex("(?i)\\b\\d{2}[A-Z]{5}\\d{4}[A-Z]{1}[A-Z\\d]{1}Z[A-Z\\d]{1}\\b")
    private val phoneRegex = Regex("(?<!\\d)(\\d{10})(?!\\d)")

    // Examples:
    // "Sugar 10 45.00 450.00"
    // "Sugar 10 45 450"
    private val spacedColumnsRegex =
        Regex("""^(.+?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""")

    // Examples:
    // "Sugar x2 @45"
    // "Sugar 2 x 45"
    private val qtyRateRegex =
        Regex("""^(.+?)\s*(?:x|×|\*)\s*(\d+(?:\.\d+)?)\s*(?:@|at)?\s*(\d+(?:\.\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    // Examples:
    // "Sugar 2 pcs 45"
    private val qtyUnitRateRegex =
        Regex("""^(.+?)\s+(\d+(?:\.\d+)?)\s*(?:pcs?|nos?|units?)\s+(\d+(?:\.\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(ocrText: String): ParsedBill {
        val lines = ocrText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val vendor = parseVendor(lines)
        val items = parseItems(lines)
        return ParsedBill(vendor = vendor, items = items)
    }

    private fun parseVendor(lines: List<String>): ParsedVendor {
        val firstNonEmpty = lines.firstOrNull().orEmpty()
        val gst = lines.asSequence().mapNotNull { gstRegex.find(it)?.value }.firstOrNull()
        val phone = lines.asSequence().mapNotNull { phoneRegex.find(it)?.groupValues?.getOrNull(1) }.firstOrNull()

        // Heuristic: vendor name is first line, unless it looks like "Invoice" / "Bill".
        val name = firstNonEmpty.takeIf { !skipLineRegex.containsMatchIn(it) }?.takeIf { it.length >= 3 }
        return ParsedVendor(name = name, gstNumber = gst, phone = phone)
    }

    private fun parseItems(lines: List<String>): List<ParsedBillItem> {
        val items = mutableListOf<ParsedBillItem>()
        for (raw in lines) {
            val line = raw
                .replace(Regex("\\s+"), " ")
                .trim()
            if (line.isBlank()) continue
            if (skipLineRegex.containsMatchIn(line)) continue
            if (line.length < 3) continue

            // Try common "columns" pattern: name qty unit total.
            val m1 = spacedColumnsRegex.find(line)
            if (m1 != null) {
                val name = m1.groupValues[1].trim().trim('-').trim()
                val qty = m1.groupValues[2].toDoubleOrNull()
                val unit = m1.groupValues[3].toDoubleOrNull()
                val total = m1.groupValues[4].toDoubleOrNull()
                val qInt = qty?.toIntOrNullSafe()
                if (!name.isNullOrBlank() && qInt != null && qInt > 0) {
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = qInt,
                            unitPrice = unit,
                            total = total,
                            rawLine = raw
                        )
                    )
                    continue
                }
            }

            // Try "name x qty @ price" pattern.
            val m2 = qtyRateRegex.find(line)
            if (m2 != null) {
                val name = m2.groupValues[1].trim().trim('-').trim()
                val qty = m2.groupValues[2].toDoubleOrNull()
                val unit = m2.groupValues[3].toDoubleOrNull()
                val qInt = qty?.toIntOrNullSafe()
                if (!name.isNullOrBlank() && qInt != null && qInt > 0) {
                    val total = if (unit != null) unit * qInt else null
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = qInt,
                            unitPrice = unit,
                            total = total,
                            rawLine = raw
                        )
                    )
                    continue
                }
            }

            // Try "name qty pcs unitPrice" pattern.
            val m3 = qtyUnitRateRegex.find(line)
            if (m3 != null) {
                val name = m3.groupValues[1].trim().trim('-').trim()
                val qty = m3.groupValues[2].toDoubleOrNull()
                val unit = m3.groupValues[3].toDoubleOrNull()
                val qInt = qty?.toIntOrNullSafe()
                if (!name.isNullOrBlank() && qInt != null && qInt > 0) {
                    val total = if (unit != null) unit * qInt else null
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = qInt,
                            unitPrice = unit,
                            total = total,
                            rawLine = raw
                        )
                    )
                    continue
                }
            }
        }

        // De-dupe: OCR often repeats the same line twice.
        return items
            .distinctBy { (it.name.lowercase() + "|" + it.qty + "|" + (it.unitPrice ?: 0.0)) }
    }

    private fun Double.toIntOrNullSafe(): Int? {
        if (this.isNaN() || this.isInfinite()) return null
        val rounded = kotlin.math.round(this).toInt()
        return rounded
    }
}


