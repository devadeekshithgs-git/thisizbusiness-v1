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
        val qtyRaw: Double? = null,
        val unit: String? = null, // e.g. PCS | KG | G
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

    private val headerLineRegex = Regex(
        pattern = "(?i)\\b(item|particulars?|description|product)\\b.*\\b(qty|quantity)\\b.*\\b(rate|price|mrp)\\b"
    )

    private val gstRegex = Regex("(?i)\\b\\d{2}[A-Z]{5}\\d{4}[A-Z]{1}[A-Z\\d]{1}Z[A-Z\\d]{1}\\b")
    private val phoneRegex = Regex("(?<!\\d)(\\d{10})(?!\\d)")
    private val vendorPrefixRegex = Regex("(?i)^(m\\.?\\s*/\\s*s\\.?\\s+|m/s\\s+|ms\\.?\\s+)")

    // Common vendor suffix/stopwords we don't want to over-weight when matching names.
    private val vendorStopwords = setOf(
        "pvt", "pvt.", "ltd", "ltd.", "limited", "private", "llp",
        "store", "stores", "mart", "traders", "trader", "enterprise", "enterprises",
        "agency", "agencies", "distributor", "distributors", "wholesale", "retail",
        "and", "&", "the", "co", "co.", "company"
    )

    // Examples:
    // "Sugar 10 45.00 450.00"
    // "Sugar 10 45 450"
    private val spacedColumnsRegex =
        Regex("""^(.+?)\s+(\d+(?:\.\d+)?)\s+([₹\d][\d,]*(?:\.\d+)?)\s+([₹\d][\d,]*(?:\.\d+)?)$""")

    // Examples:
    // "Sugar x2 @45"
    // "Sugar 2 x 45"
    private val qtyRateRegex =
        Regex("""^(.+?)\s*(?:x|×|\*)\s*(\d+(?:\.\d+)?)\s*(?:@|at)?\s*(\d+(?:\.\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    // Examples:
    // "Sugar 2 pcs 45"
    private val qtyUnitRateRegex =
        Regex("""^(.+?)\s+(\d+(?:\.\d+)?)\s*(?:pcs?|nos?|units?|kg|kgs|g|gm|gms|grams?)\s+(\d+(?:\.\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    // Examples:
    // "Sugar 45 90"  -> name rate amount (qty=1)
    private val rateAmountRegex =
        Regex("""^(.+?)\s+([₹\d][\d,]*(?:\.\d+)?)\s+([₹\d][\d,]*(?:\.\d+)?)$""")

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
        val gst = lines.asSequence().mapNotNull { gstRegex.find(it)?.value }.firstOrNull()
        val phone = lines.asSequence().mapNotNull { phoneRegex.find(it)?.groupValues?.getOrNull(1) }.firstOrNull()

        // Heuristics:
        // - vendor name is usually in the first few lines
        // - sometimes appears just before GSTIN / Phone line
        val candidateLines = buildList {
            addAll(lines.take(10))

            // If GST line exists, try the line immediately before it.
            val gstIdx = lines.indexOfFirst { gstRegex.containsMatchIn(it) }
            if (gstIdx > 0) add(lines[gstIdx - 1])

            // If phone line exists, try the line immediately before it.
            val phoneIdx = lines.indexOfFirst { phoneRegex.containsMatchIn(it) }
            if (phoneIdx > 0) add(lines[phoneIdx - 1])
        }

        val name = candidateLines
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.length >= 3 }
            .filterNot { skipLineRegex.containsMatchIn(it) }
            .map { it.replace(vendorPrefixRegex, "").trim() }
            .filter { it.length >= 3 }
            .maxByOrNull { vendorNameScore(it) }
            ?.takeIf { vendorNameScore(it) >= 2 }

        return ParsedVendor(name = name, gstNumber = gst, phone = phone)
    }

    private fun vendorNameScore(s: String): Int {
        // Higher = more likely a vendor name line.
        val t = s.trim()
        if (t.isBlank()) return 0
        if (t.length > 60) return 0

        val hasLetters = t.any { it.isLetter() }
        if (!hasLetters) return 0

        // Penalize lines that are mostly numeric/symbols.
        val digits = t.count { it.isDigit() }
        if (digits >= (t.length / 2)) return 0

        val tokens = t.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val meaningful = tokens.count { it !in vendorStopwords && it.length >= 2 }
        val stop = tokens.count { it in vendorStopwords }

        var score = 0
        if (t.length in 4..40) score += 2
        if (meaningful >= 2) score += 2
        if (stop >= 1) score += 1
        if (t.contains("gst", ignoreCase = true)) score -= 2
        if (t.contains("invoice", ignoreCase = true)) score -= 2
        if (t.contains("tax", ignoreCase = true) && t.contains("invoice", ignoreCase = true)) score -= 2
        return score
    }

    private fun parseItems(lines: List<String>): List<ParsedBillItem> {
        val items = mutableListOf<ParsedBillItem>()
        val startIdx = lines.indexOfFirst { headerLineRegex.containsMatchIn(it) }.let { if (it >= 0) it + 1 else 0 }
        for (raw in lines.drop(startIdx)) {
            val line = raw
                .replace(Regex("\\s+"), " ")
                .trim()
            if (line.isBlank()) continue
            if (skipLineRegex.containsMatchIn(line)) continue
            if (line.length < 3) continue
            if (headerLineRegex.containsMatchIn(line)) continue
            // Skip lines that are clearly headings/metadata (often mis-parsed as items)
            if (line.length <= 4) continue
            if (line.count { it.isDigit() } >= (line.length * 0.7)) continue

            // Try common "columns" pattern: name qty unit total.
            val m1 = spacedColumnsRegex.find(line)
            if (m1 != null) {
                val name = m1.groupValues[1].trim().trim('-').trim()
                val qtyRaw = m1.groupValues[2].toDoubleOrNull()
                val unitPrice = parseNumber(m1.groupValues[3])
                val total = parseNumber(m1.groupValues[4])
                val qInt = qtyRaw?.toIntOrNullSafe()
                if (!name.isNullOrBlank() && qInt != null && qInt > 0) {
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = qInt,
                            qtyRaw = qtyRaw,
                            unit = "PCS",
                            unitPrice = unitPrice,
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
                val qtyRaw = m2.groupValues[2].toDoubleOrNull()
                val unitPrice = m2.groupValues[3].toDoubleOrNull()
                val qInt = qtyRaw?.toIntOrNullSafe()
                if (!name.isNullOrBlank() && qInt != null && qInt > 0) {
                    val total = if (unitPrice != null) unitPrice * qInt else null
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = qInt,
                            qtyRaw = qtyRaw,
                            unit = "PCS",
                            unitPrice = unitPrice,
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
                val qtyRaw = m3.groupValues[2].toDoubleOrNull()
                val unitToken = line.lowercase().split(" ").firstOrNull { it in setOf("kg", "kgs", "g", "gm", "gms", "grams", "pcs", "pc", "nos", "units") }
                val unit = when {
                    unitToken == null -> "PCS"
                    unitToken.startsWith("kg") -> "KG"
                    unitToken.startsWith("g") -> "G"
                    else -> "PCS"
                }
                val unitPrice = m3.groupValues[3].toDoubleOrNull()
                val qInt = qtyRaw?.toIntOrNullSafe()
                if (!name.isNullOrBlank() && qInt != null && qInt > 0) {
                    val total = if (unitPrice != null) unitPrice * qInt else null
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = qInt,
                            qtyRaw = qtyRaw,
                            unit = unit,
                            unitPrice = unitPrice,
                            total = total,
                            rawLine = raw
                        )
                    )
                    continue
                }
            }

            // Try "name rate amount" (qty defaults to 1)
            val m4 = rateAmountRegex.find(line)
            if (m4 != null) {
                val name = m4.groupValues[1].trim().trim('-').trim()
                val unitPrice = parseNumber(m4.groupValues[2])
                val total = parseNumber(m4.groupValues[3])
                if (!name.isNullOrBlank() && unitPrice != null && total != null) {
                    items.add(
                        ParsedBillItem(
                            name = name,
                            qty = 1,
                            qtyRaw = 1.0,
                            unit = "PCS",
                            unitPrice = unitPrice,
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

    private fun parseNumber(token: String): Double? {
        val cleaned = token
            .replace("₹", "")
            .replace(",", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }
}



