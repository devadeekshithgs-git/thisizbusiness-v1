package com.kiranaflow.app.util

/**
 * Phase 5 placeholder: wire FunctionGemma (on-device) here.
 *
 * Current implementation:
 * - Uses a strengthened heuristic parser as the extraction backend.
 * - Keeps the API surface so we can swap in on-device FunctionGemma later without touching callers.
 */
class FunctionGemmaBillExtractionEngine : BillExtractionEngine {
    override suspend fun extract(ocrText: String): BillOcrParser.ParsedBill? {
        // TODO(phase5-functiongemma): integrate LiteRT-LM + FunctionGemma function-calling model.
        // For now, use our improved heuristic extractor and only return non-null when we found line items.
        val parsed = BillOcrParser.parse(ocrText)
        return parsed.takeIf { it.items.isNotEmpty() }
    }
}


