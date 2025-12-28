package com.kiranaflow.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPdfRenderer {
    // Match existing receipt look (80mm-ish). We keep the same width used for bitmap receipts so alignment is consistent.
    private const val WIDTH_PX = 576

    fun renderToPdfUri(context: Context, data: ReceiptRenderData, fileName: String): Uri {
        val pad = 24f

        fun fmtMoney(v: Double): String = "₹" + String.format(Locale.getDefault(), "%.2f", v)
        fun fmtQty(q: Double): String = String.format(Locale.getDefault(), "%.1f", q)

        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dt = Date(data.createdAtMillis)
        val dateStr = dateFmt.format(dt)
        val timeStr = timeFmt.format(dt)

        // Height estimate (tuned for this receipt format). PdfDocument needs a fixed height per page.
        val baseLines = 18
        val perItemLines = 2
        val heightPx =
            ((pad * 2) + ((baseLines + (data.items.size * perItemLines)) * 28f) + 220f).toInt()
                .coerceAtLeast(900)

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(WIDTH_PX, heightPx, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        var y = pad + 8f
        fun nextLine(step: Float = 28f) {
            y += step
        }

        fun dottedSeparator() {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            }
            canvas.drawLine(pad, y, WIDTH_PX - pad, y, p)
            nextLine(18f)
        }

        fun drawLeftRight(left: String, right: String, paint: Paint = normalPaint) {
            canvas.drawText(left, pad, y, paint)
            canvas.drawText(right, WIDTH_PX - pad, y, Paint(paint).apply { textAlign = Paint.Align.RIGHT })
            nextLine()
        }

        // Header
        canvas.drawText(data.shopName.ifBlank { "SHOP" }.uppercase(), WIDTH_PX / 2f, y, titlePaint)
        nextLine(34f)
        val phone = data.shopPhone?.trim().orEmpty()
        if (phone.isNotBlank()) {
            val phLine = "PH: $phone"
            canvas.drawText(phLine, WIDTH_PX / 2f, y, Paint(normalPaint).apply { textAlign = Paint.Align.CENTER })
            nextLine(30f)
        } else {
            nextLine(10f)
        }
        dottedSeparator()

        // Bill meta
        drawLeftRight("Bill No: ${data.billNo}", "Date: $dateStr", normalPaint)
        val custName = data.customerName?.takeIf { it.isNotBlank() } ?: "-"
        drawLeftRight("Customer: $custName", "Time: $timeStr", normalPaint)
        val custPhone = data.customerPhone?.takeIf { it.isNotBlank() } ?: "-"
        drawLeftRight("Mobile No: $custPhone", "", normalPaint)
        dottedSeparator()

        drawLeftRight("Transaction:", "Payment:", normalPaint)
        drawLeftRight(data.txTypeLabel, data.paymentLabel, boldPaint)
        dottedSeparator()

        // Table header
        val colQtyX = 340f
        val colRateX = 430f
        val colAmtX = WIDTH_PX - pad

        canvas.drawText("Item Name", pad, y, boldPaint)
        canvas.drawText("Qty", colQtyX, y, boldPaint)
        canvas.drawText("Rate", colRateX, y, boldPaint)
        canvas.drawText("Amount", colAmtX, y, rightBoldPaint)
        nextLine(30f)
        dottedSeparator()

        // Items
        data.items.forEach { it ->
            canvas.drawText(it.name, pad, y, normalPaint)
            canvas.drawText(fmtQty(it.qty), colQtyX, y, normalPaint)
            canvas.drawText(fmtMoney(it.unitPrice), colRateX, y, normalPaint)
            canvas.drawText(fmtMoney(it.lineTotal), colAmtX, y, rightPaint)
            nextLine(26f)
            canvas.drawText("SKU: ${it.itemId}", pad, y, smallPaint)
            nextLine(32f)
        }

        dottedSeparator()
        canvas.drawText("Total", pad, y, boldPaint)
        canvas.drawText(fmtMoney(data.totalAmount), colAmtX, y, rightBoldPaint)
        nextLine(32f)
        dottedSeparator()

        // Footer
        val centerSmall = Paint(smallPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("Thank you for shopping with us!", WIDTH_PX / 2f, y + 24f, centerSmall)
        nextLine(34f)
        canvas.drawText("Visit again, We value your business", WIDTH_PX / 2f, y + 10f, centerSmall)
        nextLine(34f)
        dottedSeparator()
        canvas.drawText("Powered by", WIDTH_PX / 2f, y + 20f, centerSmall)
        nextLine(40f)
        canvas.drawText(
            "thisizbusiness",
            WIDTH_PX / 2f,
            y + 10f,
            Paint(centerSmall).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        )

        pdf.finishPage(page)

        val dir = File(context.cacheDir, "digital_bills").apply { mkdirs() }
        val safeName = fileName.trim().ifBlank { "bill.pdf" }
        val file = File(dir, safeName)
        FileOutputStream(file).use { out ->
            pdf.writeTo(out)
        }
        pdf.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}








