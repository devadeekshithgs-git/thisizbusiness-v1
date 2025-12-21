package com.kiranaflow.app.util

/**
 * Runs best-available bill extraction with a safe fallback.
 */
object BillExtractionPipeline {
    private val functionGemma: BillExtractionEngine = FunctionGemmaBillExtractionEngine()

    suspend fun extract(ocrText: String): BillOcrParser.ParsedBill {
        val cleaned = BillTextNormalizer.normalize(ocrText)
        if (cleaned.isBlank()) return BillOcrParser.ParsedBill(BillOcrParser.ParsedVendor(), emptyList())

        // Prefer FunctionGemma if available; otherwise fall back.
        return functionGemma.extract(cleaned)
            ?: BillOcrParser.parse(cleaned)
    }
}


