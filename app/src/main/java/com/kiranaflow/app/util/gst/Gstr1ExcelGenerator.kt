package com.kiranaflow.app.util.gst

import com.kiranaflow.app.ui.screens.gst.Gstr1ReviewUiState
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Gstr1ExcelGenerator {
    private val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    /**
     * Generate an XLSX workbook for review/sharing.
     * Returns the file bytes (write to Downloads via MediaStore or share via SAF).
     */
    fun generateXlsx(state: Gstr1ReviewUiState): ByteArray {
        val wb: Workbook = XSSFWorkbook()

        val headerStyle = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            val font = wb.createFont().apply { bold = true }
            setFont(font)
        }

        val invoicesSheet = wb.createSheet("Invoices")
        val invoiceHeaders = listOf(
            "InvoiceNo", "Date", "RecipientGSTIN", "RecipientName", "POS",
            "Item", "HSN", "GST%", "Qty", "Unit",
            "TaxableValue", "CGST", "SGST", "IGST", "LineTotal"
        )
        writeHeaderRow(invoicesSheet, invoiceHeaders, headerStyle)

        var rowIdx = 1
        state.invoices.forEach { inv ->
            inv.lineItems.forEach { li ->
                val row = invoicesSheet.createRow(rowIdx++)
                val pos = if (inv.placeOfSupplyStateCode == 0) "" else inv.placeOfSupplyStateCode.toString().padStart(2, '0')
                val lineTotal = li.taxableValue + li.cgstAmount + li.sgstAmount + li.igstAmount

                row.createCell(0).setCellValue(inv.invoiceNumber)
                row.createCell(1).setCellValue(dateFmt.format(Date(inv.invoiceDateMillis)))
                row.createCell(2).setCellValue(inv.recipientGstin)
                row.createCell(3).setCellValue(inv.recipientName)
                row.createCell(4).setCellValue(pos)

                row.createCell(5).setCellValue(li.itemName)
                row.createCell(6).setCellValue(li.hsnCode)
                row.createCell(7).setCellValue(li.gstRate)
                row.createCell(8).setCellValue(li.qty.toDouble())
                row.createCell(9).setCellValue(li.unit)

                row.createCell(10).setCellValue(li.taxableValue)
                row.createCell(11).setCellValue(li.cgstAmount)
                row.createCell(12).setCellValue(li.sgstAmount)
                row.createCell(13).setCellValue(li.igstAmount)
                row.createCell(14).setCellValue(lineTotal)
            }
        }

        autosize(invoicesSheet, invoiceHeaders.size)

        val hsnSheet = wb.createSheet("HSN Summary")
        val hsnHeaders = listOf("HSN", "Qty", "TaxableValue", "CGST", "SGST", "IGST")
        writeHeaderRow(hsnSheet, hsnHeaders, headerStyle)
        var h = 1
        state.hsnSummary.forEach { r ->
            val row = hsnSheet.createRow(h++)
            row.createCell(0).setCellValue(r.hsnCode)
            row.createCell(1).setCellValue(r.qty)
            row.createCell(2).setCellValue(r.taxableValue)
            row.createCell(3).setCellValue(r.cgstAmount)
            row.createCell(4).setCellValue(r.sgstAmount)
            row.createCell(5).setCellValue(r.igstAmount)
        }
        autosize(hsnSheet, hsnHeaders.size)

        val bos = ByteArrayOutputStream()
        wb.use { it.write(bos) }
        return bos.toByteArray()
    }

    private fun writeHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet, headers: List<String>, style: org.apache.poi.ss.usermodel.CellStyle) {
        val row = sheet.createRow(0)
        headers.forEachIndexed { idx, h ->
            val cell = row.createCell(idx)
            cell.setCellValue(h)
            cell.cellStyle = style
        }
    }

    private fun autosize(sheet: org.apache.poi.ss.usermodel.Sheet, columns: Int) {
        for (i in 0 until columns) {
            runCatching { sheet.autoSizeColumn(i) }
        }
    }
}



