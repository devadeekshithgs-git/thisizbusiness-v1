package com.kiranaflow.app.util

/**
 * Abstraction for converting OCR text into structured vendor-bill data.
 *
 * Today we use a heuristic parser ([BillOcrParser]).
 * Phase 5 introduces a FunctionGemma-backed extractor while keeping a safe fallback.
 */
interface BillExtractionEngine {
    suspend fun extract(ocrText: String): BillOcrParser.ParsedBill?
}


