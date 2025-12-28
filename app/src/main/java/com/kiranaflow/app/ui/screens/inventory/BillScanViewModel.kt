package com.kiranaflow.app.ui.screens.inventory

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.ChangeType
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.LearningStore
import com.kiranaflow.app.data.local.ScannedBillDraft
import com.kiranaflow.app.data.local.ScannedItemDraft
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.util.BillExtractionPipeline
import com.kiranaflow.app.util.CorrectionLogger
import com.kiranaflow.app.util.EntityExtractionHelper
import com.kiranaflow.app.util.InventoryDiffEngine
import com.kiranaflow.app.util.OcrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ViewModel holding the temporary scan draft so InventoryScreen can render
 * an in-place, collapsible review section before committing to DB.
 */
class BillScanViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KiranaDatabase.getDatabase(application)
    private val repo = KiranaRepository(db)

    private val _draft = MutableStateFlow<ScannedBillDraft?>(null)
    val draft: StateFlow<ScannedBillDraft?> = _draft.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun clearDraft() {
        _draft.value = null
    }

    fun updateItem(updated: ScannedItemDraft) {
        val d = _draft.value ?: return
        _draft.value = d.copy(items = d.items.map { if (it.tempId == updated.tempId) updated else it })
    }

    fun scanFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            if (_busy.value) return@launch
            _busy.value = true
            try {
                val cr = context.contentResolver
                val ocrText = withContext(Dispatchers.IO) { OcrUtils.ocrFromUri(cr, uri) }
                val parsed = BillExtractionPipeline.extract(context, ocrText)
                val learning = LearningStore(context)

                // Entity extraction is best-effort hints. We don't block on it.
                val entities = runCatching { EntityExtractionHelper.extract(ocrText) }.getOrNull()
                val vendor = parsed.vendor.copy(
                    name = parsed.vendor.name?.let { learning.applyVendorNameCorrection(it) } ?: parsed.vendor.name,
                    phone = parsed.vendor.phone ?: entities?.phones?.firstOrNull(),
                    address = parsed.vendor.address ?: entities?.addresses?.firstOrNull(),
                    invoiceDateMillis = parsed.vendor.invoiceDateMillis ?: entities?.datesEpochMillis?.minOrNull()
                )

                val existingItems = runCatching { repo.allItemsSnapshot() }.getOrDefault(emptyList())

                val drafts = parsed.items.map { line ->
                    val correctedName = learning.applyItemNameCorrection(line.name)
                    val match = InventoryDiffEngine.matchItem(line.name, existingItems)
                    val existing = match.matchedItem
                    val changeType = if (existing == null) {
                        ChangeType.NEW
                    } else {
                        InventoryDiffEngine.computeChangeType(existing, line.qty, line.unitPrice)
                    }
                    ScannedItemDraft(
                        tempId = UUID.randomUUID().toString(),
                        sourceName = line.name.trim(),
                        sourceQty = line.qty,
                        sourceCostPrice = line.unitPrice,
                        name = correctedName.trim(),
                        qty = line.qty,
                        qtyKg = null,
                        unit = line.unit ?: "PCS",
                        costPrice = line.unitPrice,
                        sellingPrice = null,
                        gstRate = line.gstRate,
                        matchedItemId = existing?.id,
                        changeType = changeType,
                        confidence = match.confidence,
                        rawLine = line.rawLine
                    )
                }

                _draft.value = ScannedBillDraft(
                    id = UUID.randomUUID().toString(),
                    scannedAtMillis = System.currentTimeMillis(),
                    imageUri = uri.toString(),
                    vendor = vendor,
                    items = drafts,
                    invoiceTotal = parsed.grandTotalAmount
                )
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Commit inventory updates + (optional) vendor expense entry will be handled
     * by existing flow for now (InventoryScreen already has RecordVendorBillPurchaseDialog).
     */
    fun commitDraft(onDone: (added: Int, updated: Int) -> Unit, onError: (String) -> Unit) {
        val d = _draft.value ?: return
        viewModelScope.launch {
            if (_busy.value) return@launch
            _busy.value = true
            try {
                // Self-learning: persist user corrections (best-effort, local only).
                runCatching { CorrectionLogger.logDraftCorrections(getApplication(), d) }

                // For now, reuse existing repository logic by mapping draft back into ParsedBill.
                // This keeps behavior stable and additive; review UI ensures user edits are applied first.
                val parsed = com.kiranaflow.app.util.BillOcrParser.ParsedBill(
                    vendor = d.vendor,
                    items = d.items.map { it.toParsedItem() }
                )
                val res = repo.processVendorBill(parsed)

                // Vendor/Expenses integration (best-effort):
                // If the vendor can be identified/created, record this as an EXPENSE with itemized lines.
                val vendorId = res.vendorId
                if (vendorId != null) {
                    val vendor = repo.partySnapshotById(vendorId)
                    if (vendor != null) {
                        // Default to CREDIT so outstanding payables increase.
                        // This is still review-first because it happens only after the user taps Done.
                        repo.recordVendorBillPurchaseWithItems(
                            vendor = vendor,
                            parsed = parsed,
                            mode = "CREDIT",
                            receiptImageUri = d.imageUri
                        )
                    }
                }

                _draft.value = null
                onDone(res.added, res.updated)
            } catch (t: Throwable) {
                onError(t.message ?: "Could not import bill")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun ScannedItemDraft.toParsedItem(): com.kiranaflow.app.util.BillOcrParser.ParsedBillItem {
        val unitPrice = this.costPrice
        val total = if (unitPrice != null) unitPrice * qty else null
        return com.kiranaflow.app.util.BillOcrParser.ParsedBillItem(
            name = name,
            qty = qty,
            qtyRaw = qty.toDouble(),
            unit = unit,
            unitPrice = unitPrice,
            total = total,
            rawLine = rawLine ?: name
        )
    }
}


