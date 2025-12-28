package com.kiranaflow.app.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiptRenderItem(
    val itemId: Int,
    val name: String,
    val qty: Double,
    val unitPrice: Double,
    val lineTotal: Double
)

data class ReceiptRenderData(
    val shopName: String,
    val shopPhone: String?,
    val billNo: String,
    val customerName: String?,
    val customerPhone: String?,
    val paymentLabel: String, // e.g., PAY_LATER / PAID
    val txTypeLabel: String = "Sales",
    val createdAtMillis: Long,
    val items: List<ReceiptRenderItem>,
    val totalAmount: Double
)

object ReceiptImageRenderer {
    // Typical 80mm thermal: 576px wide @ 203dpi. Works well for WhatsApp too.
    private const val WIDTH_PX = 576

    fun renderToBitmap(data: ReceiptRenderData): Bitmap {
        val pad = 24f
        val lineGap = 10f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        fun fmtMoney(v: Double): String = "₹" + String.format(Locale.getDefault(), "%.2f", v)
        fun fmtQty(q: Double): String = String.format(Locale.getDefault(), "%.1f", q)

        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dt = Date(data.createdAtMillis)
        val dateStr = dateFmt.format(dt)
        val timeStr = timeFmt.format(dt)

        // Layout: compute height roughly (simple, but sufficient).
        val baseLines = 18
        val perItemLines = 2
        val heightPx = ((pad * 2) + ((baseLines + (data.items.size * perItemLines)) * 28f) + 220f).toInt().coerceAtLeast(900)

        val bmp = Bitmap.createBitmap(WIDTH_PX, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        var y = pad + 8f
        fun nextLine(step: Float = 28f) { y += step }

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

        // Header
        canvas.drawText(data.shopName.ifBlank { "SHOP" }.uppercase(), WIDTH_PX / 2f, y, titlePaint)
        nextLine(34f)
        val phone = data.shopPhone?.trim().orEmpty()
        if (phone.isNotBlank()) {
            val phLine = "PH: $phone"
            canvas.drawText(phLine, WIDTH_PX / 2f, y, normalPaint.apply { textAlign = Paint.Align.CENTER })
            normalPaint.textAlign = Paint.Align.LEFT
            nextLine(30f)
        } else {
            nextLine(10f)
        }
        dottedSeparator()

        // Bill meta: left/right lines
        fun drawLeftRight(left: String, right: String, paint: Paint = normalPaint) {
            canvas.drawText(left, pad, y, paint)
            canvas.drawText(right, WIDTH_PX - pad, y, Paint(paint).apply { textAlign = Paint.Align.RIGHT })
            nextLine()
        }

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
        canvas.drawText("thisizbusiness", WIDTH_PX / 2f, y + 10f, Paint(centerSmall).apply { typeface = Typeface.DEFAULT_BOLD })

        return bmp
    }

    fun savePngToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, "digital_bills").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareImage(
        context: Context,
        imageUri: Uri,
        chooserTitle: String = "Send bill",
        packageName: String? = null,
        caption: String? = null
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!packageName.isNullOrBlank()) setPackage(packageName)
        }
        val launch = if (packageName.isNullOrBlank()) Intent.createChooser(intent, chooserTitle) else intent
        context.startActivity(launch)
    }
}









