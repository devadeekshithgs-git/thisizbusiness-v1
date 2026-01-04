package com.kiranaflow.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.kiranaflow.app.billing.model.BillSnapshot
import com.kiranaflow.app.billing.render.TableFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPdfRenderer {
    // Match existing receipt look (80mm-ish). We keep the same width used for bitmap receipts so alignment is consistent.
    private const val WIDTH_PX = 576

    fun renderBillSnapshotToPdfUri(context: Context, bill: BillSnapshot, fileName: String): Uri {
        val pad = 32f
        val lineStep = 28f
        val smallStep = 22f

        fun formatMoney(amount: Double): String {
            return "₹${String.format(Locale.getDefault(), "%.2f", amount)}"
        }

        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateStr = dateFmt.format(bill.transactionInfo.date)
        val timeStr = timeFmt.format(bill.transactionInfo.date)

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

         fun wrapText(text: String, paint: Paint, maxWidthPx: Float): List<String> {
             val trimmed = text.trim()
             if (trimmed.isBlank()) return emptyList()
             val words = trimmed.split(Regex("\\s+"))
             val lines = mutableListOf<String>()
             var current = ""
             for (w in words) {
                 val candidate = if (current.isEmpty()) w else "$current $w"
                 if (paint.measureText(candidate) <= maxWidthPx) {
                     current = candidate
                 } else {
                     if (current.isNotEmpty()) lines.add(current)
                     current = w
                 }
             }
             if (current.isNotEmpty()) lines.add(current)
             return lines
         }

         fun estimateHeightPx(): Int {
             var y = pad + 8f
             fun next(step: Float) { y += step }

             next(34f)
             val headerLines = mutableListOf<String>()
             headerLines.addAll(wrapText(bill.storeInfo.address, normalPaint, WIDTH_PX - (pad * 2)))
             headerLines.addAll(wrapText(bill.storeInfo.gstin.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }.orEmpty(), normalPaint, WIDTH_PX - (pad * 2)))
             headerLines.addAll(wrapText(bill.storeInfo.fssaiLicense.takeIf { it.isNotBlank() }?.let { "FSSAI: $it" }.orEmpty(), normalPaint, WIDTH_PX - (pad * 2)))
             headerLines.addAll(wrapText(bill.storeInfo.customerCarePhone.takeIf { it.isNotBlank() }?.let { "Customer Care: $it" }.orEmpty(), normalPaint, WIDTH_PX - (pad * 2)))
             headerLines.addAll(wrapText(bill.storeInfo.placeOfSupply.takeIf { it.isNotBlank() }?.let { "Place of Supply: $it" }.orEmpty(), normalPaint, WIDTH_PX - (pad * 2)))
             headerLines.forEach { _ -> next(lineStep) }
             next(18f)

             next(lineStep)
             next(18f)

             next(lineStep)
             next(lineStep)
             next(lineStep)
             next(18f)

             next(lineStep)
             next(lineStep)
             next(18f)

             next(lineStep)
             next(18f)

             bill.items.forEach { item ->
                 val row = TableFormatter.formatItemRow(
                     hsn = item.hsnCode,
                     description = item.name,
                     quantity = item.quantity,
                     rate = item.unitPrice,
                     value = item.totalAmount
                 )
                 row.split("\n").forEach { _ -> next(lineStep) }
                 next(10f)
             }
             next(18f)

             next(lineStep)
             next(lineStep)
             if (bill.totals.totalDiscount > 0) next(lineStep)
             next(lineStep)
             next(lineStep)
             if (bill.totals.roundingAdjustment != 0.0) next(lineStep)
             next(18f)

             next(lineStep)
             next(lineStep)
             next(lineStep)
             if (!bill.paymentInfo.upiId.isNullOrBlank()) next(lineStep)
             if (!bill.paymentInfo.cardLast4.isNullOrBlank()) next(lineStep)
             next(18f)

             if (bill.gstSummary.breakup.isNotEmpty()) {
                 next(lineStep)
                 next(lineStep)
                 next(18f)
                 bill.gstSummary.breakup.forEach { _ -> next(lineStep) }
                 next(18f)
             }

             if (!bill.transactionInfo.paymentReferenceNo.isNullOrBlank()) next(lineStep)
             next(lineStep)
             next(18f)
             next(smallStep)
             next(lineStep)
             next(lineStep)
             next(18f)
             next(lineStep)

             next(pad)
             return y.toInt().coerceAtLeast(900)
         }

         val heightPx = estimateHeightPx()
         val pdf = PdfDocument()
         val pageInfo = PdfDocument.PageInfo.Builder(WIDTH_PX, heightPx, 1).create()
         val page = pdf.startPage(pageInfo)
         val canvas = page.canvas
         canvas.drawColor(Color.WHITE)

         var y = pad + 8f
         fun nextLine(step: Float = lineStep) { y += step }

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

         fun drawLeftRight(
             left: String,
             right: String,
             leftPaint: Paint = normalPaint,
             rightPaint: Paint = Paint(leftPaint).apply { textAlign = Paint.Align.RIGHT }
         ) {
             canvas.drawText(left, pad, y, leftPaint)
             canvas.drawText(right, WIDTH_PX - pad, y, Paint(rightPaint).apply { textAlign = Paint.Align.RIGHT })
             nextLine()
         }

         canvas.drawText(bill.storeInfo.name.ifBlank { "SHOP" }.uppercase(), WIDTH_PX / 2f, y, titlePaint)
         nextLine(34f)

         val headerMaxWidth = WIDTH_PX - (pad * 2)
         val centerNormal = Paint(normalPaint).apply { textAlign = Paint.Align.CENTER }
         wrapText(bill.storeInfo.address, normalPaint, headerMaxWidth).forEach {
             canvas.drawText(it, WIDTH_PX / 2f, y, centerNormal)
             nextLine()
         }
         bill.storeInfo.gstin.takeIf { it.isNotBlank() }?.let { gstin ->
             wrapText("GSTIN: $gstin", normalPaint, headerMaxWidth).forEach {
                 canvas.drawText(it, WIDTH_PX / 2f, y, centerNormal)
                 nextLine()
             }
         }
         bill.storeInfo.fssaiLicense.takeIf { it.isNotBlank() }?.let { fssai ->
             wrapText("FSSAI: $fssai", normalPaint, headerMaxWidth).forEach {
                 canvas.drawText(it, WIDTH_PX / 2f, y, centerNormal)
                 nextLine()
             }
         }
         bill.storeInfo.customerCarePhone.takeIf { it.isNotBlank() }?.let { ph ->
             wrapText("Customer Care: $ph", normalPaint, headerMaxWidth).forEach {
                 canvas.drawText(it, WIDTH_PX / 2f, y, centerNormal)
                 nextLine()
             }
         }
         bill.storeInfo.placeOfSupply.takeIf { it.isNotBlank() }?.let { pos ->
             wrapText("Place of Supply: $pos", normalPaint, headerMaxWidth).forEach {
                 canvas.drawText(it, WIDTH_PX / 2f, y, centerNormal)
                 nextLine()
             }
         }
         dottedSeparator()

         canvas.drawText("TAX INVOICE", WIDTH_PX / 2f, y, Paint(boldPaint).apply { textAlign = Paint.Align.CENTER })
         nextLine()
         dottedSeparator()

         drawLeftRight("Bill No: ${bill.transactionInfo.billNo}", "Date: $dateStr", normalPaint)
         drawLeftRight("Customer: ${bill.customerInfo.name.ifBlank { "-" }}", "Time: $timeStr", normalPaint)
         val custPhone = bill.customerInfo.phone.takeIf { it.isNotBlank() } ?: "-"
         drawLeftRight("Mobile No: $custPhone", "", normalPaint)
         dottedSeparator()

         drawLeftRight("Transaction:", "Payment:", normalPaint)
         drawLeftRight("Sales", bill.paymentInfo.mode, boldPaint)
         dottedSeparator()

         canvas.drawText(TableFormatter.formatHeader(), pad, y, boldPaint)
         nextLine()
         dottedSeparator()

         bill.items.forEach { item ->
             val formattedRow = TableFormatter.formatItemRow(
                 hsn = item.hsnCode,
                 description = item.name,
                 quantity = item.quantity,
                 rate = item.unitPrice,
                 value = item.totalAmount
             )
             formattedRow.split("\n").forEach { line ->
                 canvas.drawText(line, pad, y, normalPaint)
                 nextLine()
             }
             nextLine(10f)
         }
         dottedSeparator()

         drawLeftRight("Items Count:", bill.totals.itemCount.toString(), normalPaint)
         drawLeftRight("Gross Sales Value:", formatMoney(bill.totals.grossSalesValue), normalPaint)
         if (bill.totals.totalDiscount > 0) {
             drawLeftRight("Total Discount:", formatMoney(bill.totals.totalDiscount), normalPaint)
         }
         drawLeftRight("Net Sales Value (Incl. GST):", formatMoney(bill.totals.netSalesValue), boldPaint, rightBoldPaint)
         drawLeftRight("Total Amount Paid:", formatMoney(bill.totals.totalAmountPaid), boldPaint, rightBoldPaint)
         if (bill.totals.roundingAdjustment != 0.0) {
             drawLeftRight("Rounding Adjustment:", formatMoney(bill.totals.roundingAdjustment), normalPaint)
         }
         dottedSeparator()

         drawLeftRight("Payment Mode:", bill.paymentInfo.mode, boldPaint)
         drawLeftRight("Payment Status:", bill.paymentInfo.status, normalPaint)
         drawLeftRight("Amount:", formatMoney(bill.paymentInfo.amount), normalPaint)
         bill.paymentInfo.upiId?.takeIf { it.isNotBlank() }?.let { upi ->
             drawLeftRight("UPI ID:", upi, normalPaint)
         }
         bill.paymentInfo.cardLast4?.takeIf { it.isNotBlank() }?.let { last4 ->
             drawLeftRight("Card:", "**** **** **** $last4", normalPaint)
         }
         dottedSeparator()

         if (bill.gstSummary.breakup.isNotEmpty()) {
             canvas.drawText("GST BREAKUP", WIDTH_PX / 2f, y, Paint(boldPaint).apply { textAlign = Paint.Align.CENTER })
             nextLine()
             canvas.drawText(TableFormatter.formatGSTHeader(), pad, y, boldPaint)
             nextLine()
             dottedSeparator()
             bill.gstSummary.breakup.forEach { breakup ->
                 val row = TableFormatter.formatGSTRow(
                     gstRate = breakup.gstRate,
                     taxableAmount = breakup.taxableAmount,
                     cgst = breakup.cgstAmount,
                     sgst = breakup.sgstAmount,
                     cess = breakup.cessAmount,
                     total = breakup.total
                 )
                 canvas.drawText(row, pad, y, normalPaint)
                 nextLine()
             }
             dottedSeparator()
         }

         bill.transactionInfo.paymentReferenceNo?.takeIf { it.isNotBlank() }?.let { ref ->
             drawLeftRight("Payment Reference No:", ref, normalPaint)
         }
         drawLeftRight("Tax Invoice No:", bill.transactionInfo.taxInvoiceNo, normalPaint)
         dottedSeparator()

         val centerSmall = Paint(smallPaint).apply { textAlign = Paint.Align.CENTER }
         canvas.drawText("Terms & Conditions Apply", WIDTH_PX / 2f, y, centerSmall)
         nextLine(smallStep)
         canvas.drawText("Thank you for shopping with us!", WIDTH_PX / 2f, y, Paint(normalPaint).apply { textAlign = Paint.Align.CENTER })
         nextLine()
         canvas.drawText("Visit again, We value your business", WIDTH_PX / 2f, y, Paint(normalPaint).apply { textAlign = Paint.Align.CENTER })
         nextLine()
         dottedSeparator()
         canvas.drawText("Powered by thisizbusiness", WIDTH_PX / 2f, y, Paint(boldPaint).apply { textAlign = Paint.Align.CENTER })

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








