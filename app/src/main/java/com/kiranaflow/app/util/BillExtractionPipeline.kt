package com.kiranaflow.app.util

import android.content.Context

/**
 * Runs best-available bill extraction with a safe fallback.
 */
object BillExtractionPipeline {
    private val functionGemma: BillExtractionEngine = FunctionGemmaBillExtractionEngine()

    suspend fun extract(context: Context, ocrText: String): BillOcrParser.ParsedBill {
        val cleaned = BillTextNormalizer.normalize(ocrText)
        if (cleaned.isBlank()) return BillOcrParser.ParsedBill(BillOcrParser.ParsedVendor(), emptyList())

        // Prefer FunctionGemma if available; otherwise fall back.
        return functionGemma.extract(context, cleaned)
            ?: BillOcrParser.parse(cleaned)
    }
}


