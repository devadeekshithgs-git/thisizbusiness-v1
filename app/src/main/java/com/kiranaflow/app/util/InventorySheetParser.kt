package com.kiranaflow.app.util

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object InventorySheetParser {
    data class InventoryRow(
        val name: String,
        val qty: Int = 0,          // delivered quantity (to add)
        val stock: Int = 0,        // delivered stock (alias; if present prefer over qty)
        val costPrice: Double? = null,
        val sellPrice: Double? = null,
        val category: String? = null,
        val vendor: String? = null
    )

    fun parse(contentResolver: ContentResolver, uri: Uri): List<InventoryRow> {
        val mime = contentResolver.getType(uri).orEmpty()
        contentResolver.openInputStream(uri)?.use { input ->
            return when {
                mime.contains("sheet", ignoreCase = true) -> parseXlsx(input)
                mime.contains("csv", ignoreCase = true) || mime.startsWith("text/") -> parseCsv(input)
                else -> {
                    // Best-effort: try XLSX first, then CSV
                    runCatching { parseXlsx(input) }.getOrNull() ?: runCatching { parseCsv(input) }.getOrDefault(emptyList())
                }
            }
        }
        return emptyList()
    }

    private fun parseCsv(input: InputStream): List<InventoryRow> {
        val rows = readCsvAll(input)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val idx = headerIndex(header)
        val out = mutableListOf<InventoryRow>()
        for (r in rows.drop(1)) {
            val name = r.getOrNull(idx.name)?.trim().orEmpty()
            if (name.isBlank()) continue
            val qty = r.getOrNull(idx.qty)?.toIntSafe() ?: 0
            val stock = r.getOrNull(idx.stock)?.toIntSafe() ?: 0
            val cost = r.getOrNull(idx.costPrice)?.toDoubleSafe()
            val sell = r.getOrNull(idx.sellPrice)?.toDoubleSafe()
            val cat = r.getOrNull(idx.category)?.trim()?.ifBlank { null }
            val vendor = r.getOrNull(idx.vendor)?.trim()?.ifBlank { null }
            out.add(
                InventoryRow(
                    name = name,
                    qty = qty,
                    stock = stock,
                    costPrice = cost,
                    sellPrice = sell,
                    category = cat,
                    vendor = vendor
                )
            )
        }
        return out
    }

    /**
     * Minimal XLSX reader (first sheet) without external deps:
     * - Reads sharedStrings + sheet1 XML
     * - Extracts cell values by row/column
     */
    private fun parseXlsx(input: InputStream): List<InventoryRow> {
        val zis = ZipInputStream(input)
        val sharedStrings = mutableListOf<String>()
        var sheetXml: ByteArray? = null

        zis.use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStrings.clear()
                    sharedStrings.addAll(parseSharedStrings(zip))
                } else if (name.startsWith("xl/worksheets/sheet") && sheetXml == null) {
                    sheetXml = zip.readBytes()
                }
                zip.closeEntry()
            }
        }

        val sheetBytes = sheetXml ?: return emptyList()
        val table = parseSheetXml(sheetBytes.inputStream(), sharedStrings)
        if (table.isEmpty()) return emptyList()

        val header = table.first().map { it.trim().lowercase() }
        val idx = headerIndex(header)
        val out = mutableListOf<InventoryRow>()

        for (r in table.drop(1)) {
            val name = r.getOrNull(idx.name)?.trim().orEmpty()
            if (name.isBlank()) continue
            val qty = r.getOrNull(idx.qty)?.toIntSafe() ?: 0
            val stock = r.getOrNull(idx.stock)?.toIntSafe() ?: 0
            val cost = r.getOrNull(idx.costPrice)?.toDoubleSafe()
            val sell = r.getOrNull(idx.sellPrice)?.toDoubleSafe()
            val cat = r.getOrNull(idx.category)?.trim()?.ifBlank { null }
            val vendor = r.getOrNull(idx.vendor)?.trim()?.ifBlank { null }
            out.add(
                InventoryRow(
                    name = name,
                    qty = qty,
                    stock = stock,
                    costPrice = cost,
                    sellPrice = sell,
                    category = cat,
                    vendor = vendor
                )
            )
        }
        return out
    }

    private data class HeaderIndex(
        val name: Int,
        val qty: Int,
        val stock: Int,
        val costPrice: Int,
        val sellPrice: Int,
        val category: Int,
        val vendor: Int
    )

    private fun headerIndex(header: List<String>): HeaderIndex {
        fun find(vararg keys: String): Int =
            header.indexOfFirst { h -> keys.any { k -> h == k } }.takeIf { it >= 0 } ?: -1

        val name = find("item_name", "name", "product_name", "product")
        val qty = find("quantity", "qty")
        val stock = find("stock")
        val cost = find("cost_price", "costprice", "cost")
        val sell = find("sell_price", "selling_price", "sellprice", "price")
        val category = find("category")
        val vendor = find("vendor", "vendor_name")
        return HeaderIndex(name, qty, stock, cost, sell, category, vendor)
    }

    private fun String.toIntSafe(): Int? = trim().filter { it.isDigit() || it == '-' }.toIntOrNull()
    private fun String.toDoubleSafe(): Double? = trim().replace(",", "").toDoubleOrNull()

    private fun readCsvAll(input: InputStream): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(input))
        val rows = mutableListOf<List<String>>()
        val sb = StringBuilder()
        val current = mutableListOf<String>()
        var inQuotes = false

        fun flushCell() {
            current.add(sb.toString())
            sb.setLength(0)
        }
        fun flushRow() {
            flushCell()
            rows.add(current.toList())
            current.clear()
        }

        while (true) {
            val ch = reader.read()
            if (ch == -1) break
            val c = ch.toChar()
            when (c) {
                '"' -> {
                    inQuotes = !inQuotes
                }
                ',' -> {
                    if (inQuotes) sb.append(c) else flushCell()
                }
                '\n' -> {
                    if (inQuotes) sb.append(c) else flushRow()
                }
                '\r' -> Unit
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty() || current.isNotEmpty()) {
            flushRow()
        }
        return rows.filter { it.any { cell -> cell.isNotBlank() } }
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")
        val strings = mutableListOf<String>()
        var currentText: StringBuilder? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") currentText = StringBuilder()
                }
                XmlPullParser.TEXT -> {
                    currentText?.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") {
                        strings.add(currentText?.toString().orEmpty())
                        currentText = null
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheetXml(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")

        val rows = mutableListOf<MutableList<String>>()
        var currentRow: MutableList<String>? = null

        var currentCellCol: Int? = null
        var currentCellType: String? = null
        var currentValue: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            val r = parser.getAttributeValue(null, "r").orEmpty()
                            currentCellCol = colIndexFromRef(r)
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentValue = null
                        }
                        "v" -> currentValue = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentValue != null) currentValue += parser.text
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            val col = currentCellCol ?: 0
                            val row = currentRow ?: mutableListOf()
                            while (row.size <= col) row.add("")
                            val raw = currentValue.orEmpty()
                            val value = if (currentCellType == "s") {
                                raw.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx).orEmpty() }.orEmpty()
                            } else raw
                            row[col] = value
                            currentCellCol = null
                            currentCellType = null
                            currentValue = null
                        }
                        "row" -> {
                            currentRow?.let { rows.add(it) }
                            currentRow = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        return rows.map { it.toList() }.filter { it.any { cell -> cell.isNotBlank() } }
    }

    private fun colIndexFromRef(cellRef: String): Int {
        // "AB12" -> "AB"
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        var result = 0
        for (ch in letters) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }
}



