package com.kiranaflow.app.util

import android.content.Context
import com.kiranaflow.app.data.local.LearningStore
import com.kiranaflow.app.data.local.ScannedBillDraft

/**
 * Records user corrections from the in-screen review flow into [LearningStore].
 *
 * Keep it transparent + small: only store high-signal mappings (names).
 */
object CorrectionLogger {

    suspend fun logDraftCorrections(context: Context, draft: ScannedBillDraft) {
        val store = LearningStore(context)

        // Vendor name correction (if user changed vendor later; currently we only auto-fill).
        val vendorName = draft.vendor.name?.trim().orEmpty()
        // We don't have a separate source vendor name yet; skip unless future UI adds editing.

        // Item name corrections: sourceName -> name
        for (it in draft.items) {
            val raw = it.sourceName.trim()
            val corrected = it.name.trim()
            if (raw.isNotBlank() && corrected.isNotBlank() && !raw.equals(corrected, ignoreCase = true)) {
                store.recordItemNameCorrection(raw, corrected)
            }
        }

        DebugLogger.log(
            location = "CorrectionLogger",
            message = "Logged bill scan corrections",
            data = mapOf(
                "vendor" to vendorName,
                "items" to draft.items.size
            )
        )
    }
}



