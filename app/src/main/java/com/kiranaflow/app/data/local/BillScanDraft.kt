package com.kiranaflow.app.data.local

import com.kiranaflow.app.util.BillOcrParser

/**
 * Temporary, review-first draft objects for scanned vendor bills.
 *
 * These are intentionally NOT Room entities: they live in-memory until the user taps Done.
 */
data class ScannedItemDraft(
    val tempId: String,
    // Raw OCR-extracted values (kept for self-learning comparisons).
    val sourceName: String,
    val sourceQty: Int,
    val sourceCostPrice: Double? = null,
    val name: String,
    val qty: Int,
    val qtyKg: Double? = null,
    val unit: String = "PCS", // PCS | KG
    val costPrice: Double? = null,
    val sellingPrice: Double? = null,
    val gstRate: Double? = null,
    val rackLocation: String? = null,
    val imageUri: String? = null,
    val matchedItemId: Int? = null,
    val changeType: ChangeType = ChangeType.NEW,
    val confidence: Float = 0.0f,
    // Keep original OCR line for debugging/self-learning.
    val rawLine: String? = null
)

enum class ChangeType { NEW, QTY_ONLY, PRICE_ONLY, QTY_AND_PRICE }

data class ScannedBillDraft(
    val id: String,
    val scannedAtMillis: Long,
    val imageUri: String?,
    val vendor: BillOcrParser.ParsedVendor,
    val items: List<ScannedItemDraft>,
    val invoiceTotal: Double? = null
)


