package com.kiranaflow.app.billing.render

object TableFormatter {
    
    // Fixed column widths for monospace alignment (in characters)
    private const val HSN_WIDTH = 8
    private const val QTY_WIDTH = 6
    private const val RATE_WIDTH = 10
    private const val VALUE_WIDTH = 12
    private const val DESCRIPTION_WIDTH = 25
    
    /**
     * Formats item table row with monospaced alignment
     * Format: HSN | Description | Qty | Rate | Value
     */
    fun formatItemRow(
        hsn: String,
        description: String,
        quantity: Double,
        rate: Double,
        value: Double
    ): String {
        val hsnCol = hsn.padEnd(HSN_WIDTH).take(HSN_WIDTH)
        val qtyCol = formatQuantity(quantity).padEnd(QTY_WIDTH).take(QTY_WIDTH)
        val rateCol = formatMoney(rate).padEnd(RATE_WIDTH).take(RATE_WIDTH)
        val valueCol = formatMoney(value).padEnd(VALUE_WIDTH).take(VALUE_WIDTH)
        
        // Handle long descriptions by wrapping them
        val descriptionLines = wrapDescription(description, DESCRIPTION_WIDTH)
        
        return buildString {
            // First line with all columns
            append("$hsnCol | ${descriptionLines.first().padEnd(DESCRIPTION_WIDTH)} | $qtyCol | $rateCol | $valueCol")
            
            // Subsequent lines for wrapped description (only description column)
            descriptionLines.drop(1).forEach { line ->
                append("\n${" ".repeat(HSN_WIDTH)} | ${line.padEnd(DESCRIPTION_WIDTH)} | ${" ".repeat(QTY_WIDTH)} | ${" ".repeat(RATE_WIDTH)} | ${" ".repeat(VALUE_WIDTH)}")
            }
        }
    }
    
    /**
     * Formats table header
     */
    fun formatHeader(): String {
        val hsnCol = "HSN".padEnd(HSN_WIDTH)
        val descCol = "Item Description".padEnd(DESCRIPTION_WIDTH)
        val qtyCol = "Qty".padEnd(QTY_WIDTH)
        val rateCol = "Net Price".padEnd(RATE_WIDTH)
        val valueCol = "Value".padEnd(VALUE_WIDTH)
        
        return "$hsnCol | $descCol | $qtyCol | $rateCol | $valueCol"
    }
    
    /**
     * Formats table separator line
     */
    fun formatSeparator(): String {
        val separator = "-".repeat(HSN_WIDTH + DESCRIPTION_WIDTH + QTY_WIDTH + RATE_WIDTH + VALUE_WIDTH + 12) // +12 for " | " separators
        return separator
    }
    
    /**
     * Formats GST breakup table row
     * Format: GST | Taxable Amount | CGST | SGST | CESS | Total
     */
    fun formatGSTRow(
        gstRate: Double,
        taxableAmount: Double,
        cgst: Double,
        sgst: Double,
        cess: Double,
        total: Double
    ): String {
        val gstCol = "${gstRate.toInt()}%".padEnd(6)
        val taxableCol = formatMoney(taxableAmount).padEnd(14)
        val cgstCol = formatMoney(cgst).padEnd(10)
        val sgstCol = formatMoney(sgst).padEnd(10)
        val cessCol = formatMoney(cess).padEnd(10)
        val totalCol = formatMoney(total).padEnd(12)
        
        return "$gstCol | $taxableCol | $cgstCol | $sgstCol | $cessCol | $totalCol"
    }
    
    /**
     * Formats GST header
     */
    fun formatGSTHeader(): String {
        return "GST   | Taxable Amount | CGST      | SGST      | CESS      | Total     "
    }
    
    private fun wrapDescription(description: String, maxWidth: Int): List<String> {
        if (description.length <= maxWidth) return listOf(description)
        
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        description.split(" ").forEach { word ->
            if (currentLine.isEmpty()) {
                currentLine = word
            } else if ((currentLine + " " + word).length <= maxWidth) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        return lines
    }
    
    private fun formatMoney(amount: Double): String {
        return "₹${String.format("%.2f", amount)}"
    }
    
    private fun formatQuantity(quantity: Double): String {
        return if (quantity == quantity.toInt().toDouble()) {
            quantity.toInt().toString()
        } else {
            String.format("%.3f", quantity).trimEnd('0').trimEnd('.')
        }
    }
}
