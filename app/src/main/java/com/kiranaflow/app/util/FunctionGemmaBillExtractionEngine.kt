package com.kiranaflow.app.util

import android.content.Context

/**
 * Phase 5 placeholder: wire FunctionGemma (on-device) here.
 *
 * Current implementation:
 * - Uses a strengthened heuristic parser as the extraction backend.
 * - Keeps the API surface so we can swap in on-device FunctionGemma later without touching callers.
 */
class FunctionGemmaBillExtractionEngine : BillExtractionEngine {
    override suspend fun extract(context: Context, ocrText: String): BillOcrParser.ParsedBill? {
        // FunctionGemma integration strategy:
        // - Try a function-calling extraction (when a runtime is available).
        // - Validate/sanitize the result.
        // - Fall back to the heuristic parser for safety.
        val fgParsed = runCatching { FunctionGemmaBillExtractor.tryExtract(context, ocrText) }.getOrNull()
        if (fgParsed != null && fgParsed.items.isNotEmpty()) return fgParsed

        // Fallback: heuristic extraction. Return non-null only when we found line items.
        val parsed = BillOcrParser.parse(ocrText)
        return parsed.takeIf { it.items.isNotEmpty() }
    }
}


