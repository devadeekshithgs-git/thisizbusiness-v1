package com.kiranaflow.app.util.gst

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kiranaflow.app.ui.screens.gst.EditableGstr1Invoice
import com.kiranaflow.app.ui.screens.gst.Gstr1ReviewUiState
import java.util.Calendar
import kotlin.math.roundToLong

object Gstr1JsonGenerator {
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    /**
     * Build the GSTR-1 JSON object from the reviewed/validated UI state.
     *
     * @param formatInvoiceDate must return DD-MM-YYYY (GSTN schema).
     */
    fun build(
        state: Gstr1ReviewUiState,
        formatInvoiceDate: (millis: Long) -> String
    ): Gstr1Json {
        val fp = filingPeriodFromMillis(state.fromMillis)
        val b2b = buildB2b(state.invoices.filter { it.isB2b }, formatInvoiceDate)
        val b2cs = buildB2cs(
            invoices = state.invoices.filter { !it.isB2b },
            businessStateCode = state.businessStateCode
        )
        val hsn = buildHsn(state)

        return Gstr1Json(
            gstin = state.businessGstin,
            fp = fp,
            gt = null,
            curGt = null,
            b2b = b2b,
            b2cs = b2cs,
            b2cl = emptyList(),
            hsn = hsn,
            version = "GST1.0"
        )
    }

    fun toJsonString(json: Gstr1Json): String = gson.toJson(json)

    private fun buildB2b(
        invoices: List<EditableGstr1Invoice>,
        formatInvoiceDate: (millis: Long) -> String
    ): List<Gstr1B2bRecipient> {
        val byCtin = invoices
            .filter { it.recipientGstin.isNotBlank() }
            .groupBy { it.recipientGstin.trim().uppercase() }

        return byCtin.entries
            .sortedBy { it.key }
            .map { (ctin, invs) ->
                val invList = invs
                    .sortedBy { it.invoiceDateMillis }
                    .map { inv ->
                        val pos = inv.placeOfSupplyStateCode.toString().padStart(2, '0')
                        Gstr1Invoice(
                            inum = inv.invoiceNumber.trim(),
                            idt = formatInvoiceDate(inv.invoiceDateMillis),
                            value = round2(inv.invoiceTotalValue),
                            pos = pos,
                            reverseCharge = "N",
                            supplyType = null,
                            itms = inv.lineItems.mapIndexed { idx, li ->
                                Gstr1Item(
                                    num = idx + 1,
                                    itemDetail = Gstr1ItemDetail(
                                        txval = round2(li.taxableValue),
                                        rt = round2(li.gstRate),
                                        iamt = if (li.igstAmount > 0.0) round2(li.igstAmount) else null,
                                        camt = if (li.cgstAmount > 0.0) round2(li.cgstAmount) else null,
                                        samt = if (li.sgstAmount > 0.0) round2(li.sgstAmount) else null,
                                        csamt = null
                                    )
                                )
                            }
                        )
                    }
                Gstr1B2bRecipient(ctin = ctin, inv = invList)
            }
    }

    private fun buildB2cs(invoices: List<EditableGstr1Invoice>, businessStateCode: Int): List<Gstr1B2csRow> {
        // Aggregate by POS + rate + supply type.
        data class Key(val splyTy: String, val pos: String, val rt: Double)

        val agg = linkedMapOf<Key, MutableTotals>()
        invoices.forEach { inv ->
            val pos = inv.placeOfSupplyStateCode.toString().padStart(2, '0')
            val isInter = businessStateCode != 0 &&
                inv.placeOfSupplyStateCode != 0 &&
                inv.placeOfSupplyStateCode != businessStateCode
            val splyTy = if (isInter) "INTER" else "INTRA"
            inv.lineItems.forEach { li ->
                val key = Key(splyTy = splyTy, pos = pos, rt = li.gstRate)
                val t = agg.getOrPut(key) { MutableTotals() }
                t.txval += li.taxableValue
                t.camt += li.cgstAmount
                t.samt += li.sgstAmount
                t.iamt += li.igstAmount
            }
        }

        return agg.entries.map { (k, t) ->
            Gstr1B2csRow(
                splyTy = k.splyTy,
                pos = k.pos,
                rt = round2(k.rt),
                txval = round2(t.txval),
                iamt = t.iamt.takeIf { it > 0.0 }?.let(::round2),
                camt = t.camt.takeIf { it > 0.0 }?.let(::round2),
                samt = t.samt.takeIf { it > 0.0 }?.let(::round2),
                csamt = null
            )
        }
    }

    private fun buildHsn(state: Gstr1ReviewUiState): Gstr1Hsn? {
        if (state.hsnSummary.isEmpty()) return null
        val rows = state.hsnSummary.mapIndexed { idx, r ->
            Gstr1HsnRow(
                num = idx + 1,
                hsnSc = r.hsnCode,
                desc = r.description.ifBlank { null },
                uqc = null,
                qty = r.qty.takeIf { it > 0.0 },
                value = null,
                txval = round2(r.taxableValue),
                iamt = r.igstAmount.takeIf { it > 0.0 }?.let(::round2),
                camt = r.cgstAmount.takeIf { it > 0.0 }?.let(::round2),
                samt = r.sgstAmount.takeIf { it > 0.0 }?.let(::round2),
                csamt = null
            )
        }
        return Gstr1Hsn(data = rows)
    }

    private fun filingPeriodFromMillis(fromMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromMillis
        val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val year = cal.get(Calendar.YEAR).toString()
        return month + year
    }

    private data class MutableTotals(
        var txval: Double = 0.0,
        var camt: Double = 0.0,
        var samt: Double = 0.0,
        var iamt: Double = 0.0
    )

    private fun round2(v: Double): Double = ((v * 100.0).roundToLong() / 100.0)
}


