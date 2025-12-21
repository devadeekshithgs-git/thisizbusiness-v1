package com.kiranaflow.app.util

import android.content.Context

/**
 * Abstraction for converting OCR text into structured vendor-bill data.
 *
 * Today we use a heuristic parser ([BillOcrParser]).
 * Phase 5 introduces a FunctionGemma-backed extractor while keeping a safe fallback.
 */
interface BillExtractionEngine {
    suspend fun extract(context: Context, ocrText: String): BillOcrParser.ParsedBill?
}


