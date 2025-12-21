package com.kiranaflow.app.util.gst

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.kiranaflow.app.ui.screens.gst.Gstr1ReviewUiState
import java.util.Locale
import kotlin.math.max

/**
 * Simple, dependency-free PDF generator for GST exports.
 *
 * Produces a readable A4-ish PDF with:
 * - Header (GSTIN, period)
 * - Invoice summary table (one row per invoice)
 * - HSN summary table
 *
 * Note: This is not a "GSTN upload format" (GSTN uses JSON). PDF is for review/download/sharing.
 */
object Gstr1PdfGenerator {
    private const val PAGE_W = 595  // A4 @ ~72dpi
    private const val PAGE_H = 842

    fun generatePdfBytes(
        state: Gstr1ReviewUiState,
        filingPeriod: String,
        formatInvoiceDate: (millis: Long) -> String
    ): ByteArray {
        val doc = PdfDocument()
        val out = java.io.ByteArrayOutputStream()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1f
        }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = 40f

            // Header
            canvas!!.drawText("GSTR-1 Report (Review Copy)", 40f, y, titlePaint)
            y += 18f
            canvas!!.drawText("GSTIN: ${state.businessGstin}", 40f, y, textPaint)
            y += 14f
            canvas!!.drawText("Filing Period: $filingPeriod", 40f, y, textPaint)
            y += 14f
            canvas!!.drawText("Invoices: ${state.invoices.size}", 40f, y, textPaint)

            // Page number
            canvas!!.drawText("Page $pageNo", PAGE_W - 90f, 30f, smallPaint)
            y += 18f
            canvas!!.drawLine(40f, y, (PAGE_W - 40).toFloat(), y, linePaint)
            y += 18f
        }

        fun ensureSpace(required: Float) {
            if (y + required > PAGE_H - 50f) {
                newPage()
            }
        }

        fun drawTableHeader(cols: List<Pair<String, Float>>) {
            ensureSpace(24f)
            var x = 40f
            cols.forEach { (label, w) ->
                canvas!!.drawText(label, x, y, hPaint)
                x += w
            }
            y += 10f
            canvas!!.drawLine(40f, y, (PAGE_W - 40).toFloat(), y, linePaint)
            y += 14f
        }

        fun drawRow(cols: List<Pair<String, Float>>, values: List<String>) {
            ensureSpace(16f)
            var x = 40f
            cols.forEachIndexed { idx, (_, w) ->
                val v = values.getOrNull(idx).orEmpty()
                canvas!!.drawText(ellipsize(v, w, textPaint), x, y, textPaint)
                x += w
            }
            y += 14f
        }

        newPage()

        // Invoices section
        canvas!!.drawText("Invoice Summary", 40f, y, hPaint)
        y += 14f

        val invCols = listOf(
            "InvNo" to 120f,
            "Date" to 70f,
            "Type" to 45f,
            "RecipientGSTIN" to 130f,
            "POS" to 35f,
            "Taxable" to 70f,
            "Tax" to 60f
        )
        drawTableHeader(invCols)

        state.invoices.forEach { inv ->
            val taxable = inv.lineItems.sumOf { it.taxableValue }
            val tax = inv.lineItems.sumOf { it.cgstAmount + it.sgstAmount + it.igstAmount }
            val pos = if (inv.placeOfSupplyStateCode == 0) "" else inv.placeOfSupplyStateCode.toString().padStart(2, '0')
            drawRow(
                invCols,
                listOf(
                    inv.invoiceNumber,
                    formatInvoiceDate(inv.invoiceDateMillis),
                    if (inv.isB2b) "B2B" else "B2C",
                    inv.recipientGstin,
                    pos,
                    formatMoney(taxable),
                    formatMoney(tax)
                )
            )
        }

        y += 8f
        canvas!!.drawLine(40f, y, (PAGE_W - 40).toFloat(), y, linePaint)
        y += 18f

        // HSN summary section
        canvas!!.drawText("HSN Summary", 40f, y, hPaint)
        y += 14f

        val hsnCols = listOf(
            "HSN" to 60f,
            "Qty" to 60f,
            "Taxable" to 90f,
            "CGST" to 70f,
            "SGST" to 70f,
            "IGST" to 70f
        )
        drawTableHeader(hsnCols)

        if (state.hsnSummary.isEmpty()) {
            drawRow(hsnCols, listOf("-", "-", "-", "-", "-", "-"))
        } else {
            state.hsnSummary.forEach { r ->
                drawRow(
                    hsnCols,
                    listOf(
                        r.hsnCode,
                        formatQty(r.qty),
                        formatMoney(r.taxableValue),
                        formatMoney(r.cgstAmount),
                        formatMoney(r.sgstAmount),
                        formatMoney(r.igstAmount)
                    )
                )
            }
        }

        // Finish
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun formatMoney(v: Double): String {
        // Keep plain numeric for spreadsheet-like readability in PDF
        return String.format(Locale.US, "%.2f", v)
    }

    private fun formatQty(v: Double): String {
        val d = max(0.0, v)
        return if (kotlin.math.abs(d - d.toLong()) < 0.0001) d.toLong().toString() else String.format(Locale.US, "%.3f", d)
    }

    private fun ellipsize(text: String, width: Float, paint: Paint): String {
        if (text.isBlank()) return ""
        val maxWidth = width - 6f
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText("$s…") > maxWidth) {
            s = s.dropLast(1)
        }
        return if (s.isEmpty()) "" else "$s…"
    }
}


