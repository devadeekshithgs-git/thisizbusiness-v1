package com.kiranaflow.app.ui.screens.gst

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.GstSaleLineRow
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.util.gst.GstValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GstReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KiranaDatabase.getDatabase(application)
    private val txDao = db.transactionDao()
    private val shopSettingsStore = ShopSettingsStore(application)

    private val _gstr1 = MutableStateFlow(Gstr1ReviewUiState(isLoading = false))
    val gstr1: StateFlow<Gstr1ReviewUiState> = _gstr1.asStateFlow()

    // In-memory overrides (invoice-level)
    private val invoiceNumberOverride = mutableMapOf<Int, String>()
    private val recipientNameOverride = mutableMapOf<Int, String>()
    private val recipientGstinOverride = mutableMapOf<Int, String>()
    private val posStateOverride = mutableMapOf<Int, Int>()

    // In-memory overrides (line-level)
    private val lineHsnOverride = mutableMapOf<Int, String>()
    private val lineGstRateOverride = mutableMapOf<Int, Double>()
    private val lineTaxableOverride = mutableMapOf<Int, Double>()

    private var lastBaseRows: List<GstSaleLineRow> = emptyList()

    private val invoiceDateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    fun loadGstr1(fromMillis: Long, toMillisExclusive: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _gstr1.value = _gstr1.value.copy(
                isLoading = true,
                error = null,
                fromMillis = fromMillis,
                toMillisExclusive = toMillisExclusive
            )

            runCatching {
                val shop = shopSettingsStore.settings.first()
                val rows = txDao.getSaleLinesForPeriod(fromMillis, toMillisExclusive)
                lastBaseRows = rows

                val newState = buildUiState(rows, fromMillis, toMillisExclusive, shop.gstin, shop.legalName, shop.stateCode)
                _gstr1.value = newState
            }.onFailure { e ->
                _gstr1.value = _gstr1.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load sales for export"
                )
            }
        }
    }

    fun updateInvoiceNumber(txId: Int, invoiceNumber: String) {
        invoiceNumberOverride[txId] = invoiceNumber
        rebuild()
    }

    fun updateRecipientName(txId: Int, name: String) {
        recipientNameOverride[txId] = name
        rebuild()
    }

    fun updateRecipientGstin(txId: Int, gstin: String) {
        recipientGstinOverride[txId] = gstin
        rebuild()
    }

    fun updatePlaceOfSupply(txId: Int, stateCode: Int) {
        posStateOverride[txId] = stateCode
        rebuild()
    }

    fun updateLineHsn(lineId: Int, hsn: String) {
        lineHsnOverride[lineId] = hsn
        viewModelScope.launch(Dispatchers.IO) {
            txDao.updateLineHsnSnapshot(lineId, hsn.ifBlank { null })
        }
        rebuild()
    }

    fun updateLineGstRate(lineId: Int, gstRate: Double) {
        lineGstRateOverride[lineId] = gstRate
        viewModelScope.launch(Dispatchers.IO) {
            txDao.updateLineGstRate(lineId, gstRate)
        }
        rebuild()
    }

    fun updateLineTaxableValue(lineId: Int, taxable: Double) {
        lineTaxableOverride[lineId] = taxable
        viewModelScope.launch(Dispatchers.IO) {
            txDao.updateLineTaxableValue(lineId, taxable)
        }
        rebuild()
    }

    private fun rebuild() {
        val cur = _gstr1.value
        if (cur.isLoading) return
        val rows = lastBaseRows
        if (rows.isEmpty() && cur.error == null) {
            // still allow rebuild for empty periods (nil return)
        }
        _gstr1.value = buildUiState(
            baseRows = rows,
            fromMillis = cur.fromMillis,
            toMillisExclusive = cur.toMillisExclusive,
            businessGstin = cur.businessGstin,
            businessLegalName = cur.businessLegalName,
            businessStateCode = cur.businessStateCode
        )
    }

    private fun buildUiState(
        baseRows: List<GstSaleLineRow>,
        fromMillis: Long,
        toMillisExclusive: Long,
        businessGstin: String,
        businessLegalName: String,
        businessStateCode: Int
    ): Gstr1ReviewUiState {
        val byTx = baseRows.groupBy { it.txId }

        val invoices = byTx.entries
            .sortedBy { it.value.firstOrNull()?.txDate ?: 0L }
            .map { (txId, rows) ->
                val first = rows.first()

                val invoiceNumDefault = "KF-${txId}"
                val invoiceNum = invoiceNumberOverride[txId] ?: invoiceNumDefault

                val recipientName = (recipientNameOverride[txId] ?: first.customerName).orEmpty()
                val recipientGstin = (recipientGstinOverride[txId] ?: first.customerGstin).orEmpty().uppercase()
                val isB2b = recipientGstin.isNotBlank()

                val posDefault = first.customerStateCode ?: businessStateCode
                val pos = posStateOverride[txId] ?: posDefault ?: 0

                val lineItems = rows.map { r ->
                    val hsnFromItem = r.itemHsnCode.orEmpty()
                    val hsn = (lineHsnOverride[r.itemLineId] ?: r.hsnCodeSnapshot ?: hsnFromItem).orEmpty()

                    val inferredRate = when {
                        (lineGstRateOverride[r.itemLineId] ?: 0.0) > 0.0 -> lineGstRateOverride[r.itemLineId] ?: 0.0
                        r.gstRateSnapshot > 0.0 -> r.gstRateSnapshot
                        (r.itemGstPercentage ?: 0.0) > 0.0 -> r.itemGstPercentage ?: 0.0
                        else -> 0.0
                    }

                    val taxable = when {
                        (lineTaxableOverride[r.itemLineId] ?: 0.0) > 0.0 -> lineTaxableOverride[r.itemLineId] ?: 0.0
                        r.taxableValueSnapshot > 0.0 -> r.taxableValueSnapshot
                        r.unit.uppercase() in setOf("GRAM", "G", "GM", "GMS", "GRAMS") -> r.price * (r.qty / 1000.0) // backward-compat only
                        else -> r.price * r.qty
                    }

                    val isInter = businessStateCode != 0 && pos != 0 && pos != businessStateCode
                    val (cgst, sgst, igst) = computeTaxes(taxable, inferredRate, isInter)

                    EditableGstr1LineItem(
                        lineId = r.itemLineId,
                        itemName = r.itemNameSnapshot,
                        qty = r.qty,
                        unit = r.unit,
                        hsnCode = hsn,
                        gstRate = inferredRate,
                        taxableValue = taxable,
                        cgstAmount = if (isInter) 0.0 else cgst,
                        sgstAmount = if (isInter) 0.0 else sgst,
                        igstAmount = if (isInter) igst else 0.0
                    )
                }

                EditableGstr1Invoice(
                    txId = txId,
                    invoiceNumber = invoiceNum,
                    invoiceDateMillis = first.txDate,
                    invoiceTotalValue = first.txAmount,
                    recipientName = recipientName,
                    recipientGstin = recipientGstin,
                    placeOfSupplyStateCode = pos,
                    isB2b = isB2b,
                    lineItems = lineItems
                )
            }

        // Build HSN summary from the editable invoices/lines (post-overrides).
        val hsnAgg = linkedMapOf<String, Gstr1HsnSummaryRow>()
        invoices.flatMap { it.lineItems }.forEach { li ->
            val key = li.hsnCode.trim()
            if (key.isBlank()) return@forEach
            val cur = hsnAgg[key]
            val qty = (cur?.qty ?: 0.0) + li.qty
            val txval = (cur?.taxableValue ?: 0.0) + li.taxableValue
            val cgst = (cur?.cgstAmount ?: 0.0) + li.cgstAmount
            val sgst = (cur?.sgstAmount ?: 0.0) + li.sgstAmount
            val igst = (cur?.igstAmount ?: 0.0) + li.igstAmount
            hsnAgg[key] = Gstr1HsnSummaryRow(
                hsnCode = key,
                qty = qty,
                taxableValue = txval,
                cgstAmount = cgst,
                sgstAmount = sgst,
                igstAmount = igst
            )
        }

        val issues = validate(invoices, businessGstin, businessLegalName, businessStateCode)

        return Gstr1ReviewUiState(
            isLoading = false,
            error = null,
            fromMillis = fromMillis,
            toMillisExclusive = toMillisExclusive,
            businessGstin = businessGstin,
            businessLegalName = businessLegalName,
            businessStateCode = businessStateCode,
            invoices = invoices,
            hsnSummary = hsnAgg.values.toList(),
            validationIssues = issues
        )
    }

    private fun computeTaxes(taxable: Double, gstRate: Double, isInter: Boolean): Triple<Double, Double, Double> {
        if (taxable <= 0.0 || gstRate <= 0.0) return Triple(0.0, 0.0, 0.0)
        val tax = taxable * (gstRate / 100.0)
        return if (isInter) {
            Triple(0.0, 0.0, tax)
        } else {
            val half = tax / 2.0
            Triple(half, half, 0.0)
        }
    }

    private fun validate(
        invoices: List<EditableGstr1Invoice>,
        businessGstin: String,
        businessLegalName: String,
        businessStateCode: Int
    ): List<Gstr1ValidationIssue> {
        val issues = mutableListOf<Gstr1ValidationIssue>()

        if (businessGstin.isBlank()) {
            issues += Gstr1ValidationIssue(
                code = Gstr1ValidationIssue.Code.BUSINESS_GSTIN_MISSING,
                message = "Missing your GSTIN in Settings (required for export)."
            )
        } else if (!GstValidator.isValidGstin(businessGstin)) {
            issues += Gstr1ValidationIssue(
                code = Gstr1ValidationIssue.Code.BUSINESS_GSTIN_INVALID,
                message = "Your GSTIN looks invalid (checksum mismatch). Please verify in Settings."
            )
        }
        if (businessLegalName.isBlank()) {
            issues += Gstr1ValidationIssue(
                code = Gstr1ValidationIssue.Code.BUSINESS_LEGAL_NAME_MISSING,
                message = "Missing your Legal Name in Settings (required for export)."
            )
        }
        if (businessStateCode == 0) {
            issues += Gstr1ValidationIssue(
                code = Gstr1ValidationIssue.Code.BUSINESS_STATE_CODE_MISSING,
                message = "Missing your State Code in Settings (required for export)."
            )
        }

        // Invoice number duplicates (case-insensitive trim).
        val seen = mutableSetOf<String>()
        invoices.forEach { inv ->
            val inum = inv.invoiceNumber.trim()
            if (inum.isBlank()) {
                issues += Gstr1ValidationIssue(
                    code = Gstr1ValidationIssue.Code.INVOICE_NUMBER_MISSING,
                    message = "Invoice # missing for transaction ${inv.txId}.",
                    txId = inv.txId
                )
            } else {
                val key = inum.uppercase()
                if (!seen.add(key)) {
                    issues += Gstr1ValidationIssue(
                        code = Gstr1ValidationIssue.Code.INVOICE_NUMBER_DUPLICATE,
                        message = "Duplicate invoice number: $inum",
                        txId = inv.txId
                    )
                }
            }

            if (inv.placeOfSupplyStateCode == 0) {
                issues += Gstr1ValidationIssue(
                    code = Gstr1ValidationIssue.Code.INVOICE_PLACE_OF_SUPPLY_MISSING,
                    message = "Place of supply missing (state code) for invoice ${inv.invoiceNumber}.",
                    txId = inv.txId
                )
            }

            if (inv.isB2b && inv.recipientGstin.isBlank()) {
                issues += Gstr1ValidationIssue(
                    code = Gstr1ValidationIssue.Code.INVOICE_B2B_RECIPIENT_GSTIN_MISSING,
                    message = "B2B invoice requires recipient GSTIN: ${inv.invoiceNumber}",
                    txId = inv.txId
                )
            } else if (inv.isB2b && !GstValidator.isValidGstin(inv.recipientGstin)) {
                issues += Gstr1ValidationIssue(
                    code = Gstr1ValidationIssue.Code.INVOICE_B2B_RECIPIENT_GSTIN_INVALID,
                    message = "Recipient GSTIN invalid (checksum mismatch) for invoice ${inv.invoiceNumber}.",
                    txId = inv.txId
                )
            }

            inv.lineItems.forEach { li ->
                if (!GstValidator.isValidHsn(li.hsnCode)) {
                    issues += Gstr1ValidationIssue(
                        code = Gstr1ValidationIssue.Code.LINE_HSN_MISSING,
                        message = "HSN/SAC missing for '${li.itemName}' in invoice ${inv.invoiceNumber}.",
                        txId = inv.txId,
                        lineId = li.lineId
                    )
                }
                if (li.gstRate < 0.0) {
                    issues += Gstr1ValidationIssue(
                        code = Gstr1ValidationIssue.Code.LINE_GST_RATE_INVALID,
                        message = "Invalid GST % for '${li.itemName}' in invoice ${inv.invoiceNumber}.",
                        txId = inv.txId,
                        lineId = li.lineId
                    )
                }
                if (li.taxableValue <= 0.0) {
                    issues += Gstr1ValidationIssue(
                        code = Gstr1ValidationIssue.Code.LINE_TAXABLE_VALUE_MISSING,
                        message = "Taxable value missing for '${li.itemName}' in invoice ${inv.invoiceNumber}.",
                        txId = inv.txId,
                        lineId = li.lineId
                    )
                }
            }
        }

        return issues
    }

    /**
     * Used by JSON generator: convert tx date millis to DD-MM-YYYY.
     */
    fun formatInvoiceDate(millis: Long): String = invoiceDateFmt.format(Date(millis))
}


