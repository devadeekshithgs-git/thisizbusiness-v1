package com.kiranaflow.app.util

import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

object InventoryImportDemoGenerator {
    // Keep headers aligned with InventorySheetParser.headerIndex()
    private val headers = listOf(
        "item_name",   // required
        "stock",       // optional (preferred over qty if present)
        "qty",         // optional
        "cost_price",  // optional
        "sell_price",  // optional
        "category",    // optional
        "vendor"       // optional
    )

    fun generateCsv(): String {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",")).append("\n")
        sb.append("Basmati Rice 5kg,20,,450,520,Grocery,Sharma Traders\n")
        sb.append("Toor Dal 1kg,15,,120,140,Grocery,\n")
        sb.append("Soap Bar,,,12,15,Personal Care,\n")
        return sb.toString()
    }

    fun generateXlsx(): ByteArray {
        val wb: Workbook = XSSFWorkbook()

        val headerStyle = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            val font = wb.createFont().apply { bold = true }
            setFont(font)
        }

        val sheet = wb.createSheet("Inventory")

        // Header row
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { idx, h ->
            val cell = headerRow.createCell(idx)
            cell.setCellValue(h)
            cell.cellStyle = headerStyle
        }

        // Example rows
        fun row(r: Int, values: List<String>) {
            val rr = sheet.createRow(r)
            values.forEachIndexed { c, v -> rr.createCell(c).setCellValue(v) }
        }
        row(
            1,
            listOf(
                "Basmati Rice 5kg",
                "20",
                "",
                "450",
                "520",
                "Grocery",
                "Sharma Traders"
            )
        )
        row(
            2,
            listOf(
                "Toor Dal 1kg",
                "15",
                "",
                "120",
                "140",
                "Grocery",
                ""
            )
        )
        row(
            3,
            listOf(
                "Soap Bar",
                "",
                "50",
                "12",
                "15",
                "Personal Care",
                ""
            )
        )

        // Autosize for readability
        for (i in headers.indices) {
            runCatching { sheet.autoSizeColumn(i) }
        }

        val bos = ByteArrayOutputStream()
        wb.use { it.write(bos) }
        return bos.toByteArray()
    }
}


