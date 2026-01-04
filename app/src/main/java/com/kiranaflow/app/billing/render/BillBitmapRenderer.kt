package com.kiranaflow.app.billing.render

import android.graphics.*
import com.kiranaflow.app.billing.model.BillSnapshot
import java.text.SimpleDateFormat
import java.util.*

object BillBitmapRenderer {
    
    // Fixed width for thermal printer proportions with dynamic height
    private const val WIDTH_PX = 576 // Standard 80mm thermal printer width at 203dpi
    private const val PADDING = 64f // Increased margin to prevent text cutoff on left side
    private const val LINE_HEIGHT = 32f
    private const val SMALL_LINE_HEIGHT = 24f
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    fun renderToBitmap(bill: BillSnapshot): Bitmap {
        // Calculate required height first
        val requiredHeight = calculateRequiredHeight(bill)
        // Keep fixed width for better proportions, not 1:1
        
        val bitmap = createBitmap(requiredHeight)
        val canvas = createBitmapCanvas(bitmap)
        var y = PADDING + 8f
        
        // Setup paints
        val paints = createPaints()
        
        // Header Section
        y = renderHeader(canvas, bill.storeInfo, paints, y)
        y = renderTaxInvoiceHeader(canvas, paints, y)
        
        // Transaction Metadata
        y = renderTransactionMetadata(canvas, bill.transactionInfo, bill.customerInfo, paints, y)
        
        // Item Table
        y = renderItemTable(canvas, bill.items, paints, y)
        
        // Totals Section
        y = renderTotals(canvas, bill.totals, paints, y)
        
        // Payment Info
        y = renderPaymentInfo(canvas, bill.paymentInfo, paints, y)
        
        // GST Breakup - only if GST details are provided
        if (hasGSTDetails(bill.gstSummary)) {
            y = renderGSTBreakup(canvas, bill.gstSummary, paints, y)
        }
        
        // Footer
        y = renderFooter(canvas, bill.transactionInfo, paints, y)
        
        return bitmap
    }
    
    private fun createBitmap(height: Int): Bitmap {
        return Bitmap.createBitmap(WIDTH_PX, height, Bitmap.Config.ARGB_8888)
    }
    
    private fun createBitmapCanvas(bitmap: Bitmap): Canvas {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        return canvas
    }
    
    private fun createPaints(): Paints {
        return Paints(
            title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 36f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            },
            bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            },
            normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
            },
            small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 18f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
            },
            right = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            },
            rightBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
        )
    }
    
    private data class Paints(
        val title: Paint,
        val bold: Paint,
        val normal: Paint,
        val small: Paint,
        val right: Paint,
        val rightBold: Paint
    )
    
    private fun renderHeader(canvas: Canvas, store: com.kiranaflow.app.billing.model.StoreInfo, paints: Paints, y: Float): Float {
        var currentY = y
        
        // Store name (bold, centered)
        canvas.drawText(store.name.uppercase(), WIDTH_PX / 2f, currentY, paints.title)
        currentY += 40f
        
        // Address (multi-line, centered)
        val addressLines = store.address.split("\n")
        addressLines.forEach { line ->
            canvas.drawText(line.trim(), WIDTH_PX / 2f, currentY, paints.normal.apply { textAlign = Paint.Align.CENTER })
            currentY += LINE_HEIGHT
        }
        
        // GSTIN and FSSAI
        if (store.gstin.isNotBlank()) {
            canvas.drawText("GSTIN: ${store.gstin}", WIDTH_PX / 2f, currentY, paints.small.apply { textAlign = Paint.Align.CENTER })
            currentY += SMALL_LINE_HEIGHT
        }
        if (store.fssaiLicense.isNotBlank()) {
            canvas.drawText("FSSAI: ${store.fssaiLicense}", WIDTH_PX / 2f, currentY, paints.small.apply { textAlign = Paint.Align.CENTER })
            currentY += SMALL_LINE_HEIGHT
        }
        
        // Customer care
        if (store.customerCarePhone.isNotBlank()) {
            canvas.drawText("Customer Care: ${store.customerCarePhone}", WIDTH_PX / 2f, currentY, paints.small.apply { textAlign = Paint.Align.CENTER })
            currentY += SMALL_LINE_HEIGHT
        }
        
        currentY += 10f
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderTaxInvoiceHeader(canvas: Canvas, paints: Paints, y: Float): Float {
        var currentY = y
        
        canvas.drawText("TAX INVOICE", WIDTH_PX / 2f, currentY, paints.bold.apply { textAlign = Paint.Align.CENTER })
        currentY += LINE_HEIGHT
        
        canvas.drawText("Original for Recipient", WIDTH_PX / 2f, currentY, paints.normal.apply { textAlign = Paint.Align.CENTER })
        currentY += LINE_HEIGHT
        
        canvas.drawText("Place of Supply: ${getPlaceOfSupply()}", WIDTH_PX / 2f, currentY, paints.small.apply { textAlign = Paint.Align.CENTER })
        currentY += SMALL_LINE_HEIGHT
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderTransactionMetadata(
        canvas: Canvas,
        tx: com.kiranaflow.app.billing.model.TransactionInfo,
        customer: com.kiranaflow.app.billing.model.CustomerInfo,
        paints: Paints,
        y: Float
    ): Float {
        var currentY = y
        
        val dateStr = dateFormat.format(tx.date)
        val timeStr = timeFormat.format(tx.date)
        
        // Left/Right layout
        drawLeftRight(canvas, "Date & Time: $dateStr $timeStr", "Bill No: ${tx.billNo}", paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        drawLeftRight(canvas, "Store ID: ${tx.storeId}", "POS No: ${tx.posNo}", paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        drawLeftRight(canvas, "Cashier ID: ${tx.cashierId}", "Customer Type: ${customer.type}", paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        drawLeftRight(canvas, "Customer: ${customer.name}", "Mobile: ${customer.phone}", paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderItemTable(canvas: Canvas, items: List<com.kiranaflow.app.billing.model.BillItem>, paints: Paints, y: Float): Float {
        var currentY = y
        
        // Table header
        canvas.drawText(TableFormatter.formatHeader(), PADDING, currentY, paints.bold)
        currentY += LINE_HEIGHT
        
        drawDottedLine(canvas, currentY)
        currentY += 20f
        
        // Items
        items.forEach { item ->
            val formattedRow = TableFormatter.formatItemRow(
                hsn = item.hsnCode,
                description = item.name,
                quantity = item.quantity,
                rate = item.unitPrice,
                value = item.totalAmount
            )
            
            // Handle multi-line rows
            val lines = formattedRow.split("\n")
            lines.forEach { line ->
                canvas.drawText(line, PADDING, currentY, paints.normal)
                currentY += LINE_HEIGHT
            }
            currentY += 10f // Extra spacing between items
        }
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderTotals(canvas: Canvas, totals: com.kiranaflow.app.billing.model.BillTotals, paints: Paints, y: Float): Float {
        var currentY = y
        
        drawLeftRight(canvas, "Items Count: ${totals.itemCount}", "", paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        drawLeftRight(canvas, "Gross Sales Value:", formatMoney(totals.grossSalesValue), paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        if (totals.totalDiscount > 0) {
            drawLeftRight(canvas, "Total Discount:", formatMoney(totals.totalDiscount), paints.normal, currentY)
            currentY += LINE_HEIGHT
        }
        
        drawLeftRight(canvas, "Net Sales Value (Incl. GST):", formatMoney(totals.netSalesValue), paints.bold, currentY)
        currentY += LINE_HEIGHT
        
        drawLeftRight(canvas, "Total Amount Paid:", formatMoney(totals.totalAmountPaid), paints.rightBold, currentY)
        currentY += LINE_HEIGHT
        
        if (totals.roundingAdjustment != 0.0) {
            drawLeftRight(canvas, "Rounding Adjustment:", formatMoney(totals.roundingAdjustment), paints.normal, currentY)
            currentY += LINE_HEIGHT
        }
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderPaymentInfo(canvas: Canvas, payment: com.kiranaflow.app.billing.model.PaymentInfo, paints: Paints, y: Float): Float {
        var currentY = y
        
        drawLeftRight(canvas, "Payment Mode:", payment.mode, paints.bold, currentY)
        currentY += LINE_HEIGHT
        
        drawLeftRight(canvas, "Payment Status:", payment.status, paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        if (!payment.upiId.isNullOrBlank()) {
            drawLeftRight(canvas, "UPI ID:", payment.upiId ?: "", paints.normal, currentY)
            currentY += LINE_HEIGHT
        }
        
        if (!payment.cardLast4.isNullOrBlank()) {
            drawLeftRight(canvas, "Card:", "**** **** **** ${payment.cardLast4}", paints.normal, currentY)
            currentY += LINE_HEIGHT
        }
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderGSTBreakup(canvas: Canvas, gst: com.kiranaflow.app.billing.model.GSTSummary, paints: Paints, y: Float): Float {
        var currentY = y
        
        canvas.drawText("GST BREAKUP", WIDTH_PX / 2f, currentY, paints.bold.apply { textAlign = Paint.Align.CENTER })
        currentY += LINE_HEIGHT + 10f
        
        // GST header
        canvas.drawText(TableFormatter.formatGSTHeader(), PADDING, currentY, paints.bold)
        currentY += LINE_HEIGHT
        
        drawDottedLine(canvas, currentY)
        currentY += 20f
        
        // GST breakup rows
        gst.breakup.forEach { breakup ->
            val row = TableFormatter.formatGSTRow(
                gstRate = breakup.gstRate,
                taxableAmount = breakup.taxableAmount,
                cgst = breakup.cgstAmount,
                sgst = breakup.sgstAmount,
                cess = breakup.cessAmount,
                total = breakup.total
            )
            canvas.drawText(row, PADDING, currentY, paints.normal)
            currentY += LINE_HEIGHT
        }
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        return currentY
    }
    
    private fun renderFooter(canvas: Canvas, tx: com.kiranaflow.app.billing.model.TransactionInfo, paints: Paints, y: Float): Float {
        var currentY = y
        
        if (!tx.paymentReferenceNo.isNullOrBlank()) {
            drawLeftRight(canvas, "Payment Reference No:", tx.paymentReferenceNo ?: "", paints.normal, currentY)
            currentY += LINE_HEIGHT
        }
        
        drawLeftRight(canvas, "Tax Invoice No:", tx.taxInvoiceNo, paints.normal, currentY)
        currentY += LINE_HEIGHT
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        // Footer messages
        canvas.drawText("Terms & Conditions Apply", WIDTH_PX / 2f, currentY, paints.small.apply { textAlign = Paint.Align.CENTER })
        currentY += SMALL_LINE_HEIGHT + 10f
        
        canvas.drawText("Thank you for shopping with us!", WIDTH_PX / 2f, currentY, paints.normal.apply { textAlign = Paint.Align.CENTER })
        currentY += LINE_HEIGHT
        
        canvas.drawText("Visit again, We value your business", WIDTH_PX / 2f, currentY, paints.normal.apply { textAlign = Paint.Align.CENTER })
        currentY += LINE_HEIGHT + 20f
        
        drawDottedLine(canvas, currentY)
        currentY += 25f
        
        canvas.drawText("Powered by thisizbusiness", WIDTH_PX / 2f, currentY, paints.bold.apply { textAlign = Paint.Align.CENTER })
        
        return currentY
    }
    
    private fun drawLeftRight(canvas: Canvas, left: String, right: String, paint: Paint, y: Float) {
        canvas.drawText(left, PADDING, y, paint)
        val rightPaint = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(right, WIDTH_PX - PADDING, y, rightPaint)
    }
    
    private fun drawDottedLine(canvas: Canvas, y: Float) {
        val dottedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }
        canvas.drawLine(PADDING, y, WIDTH_PX - PADDING, y, dottedPaint)
    }
    
    private fun formatMoney(amount: Double): String {
        return "₹${String.format("%.2f", amount)}"
    }
    
    private fun getPlaceOfSupply(): String {
        // Default to Karnataka for now - should be configurable
        return "Karnataka (29)"
    }
    
    /**
     * Calculates the required height for the bitmap based on actual content
     */
    private fun calculateRequiredHeight(bill: BillSnapshot): Int {
        var height = 0f
        
        // Initial padding
        height += PADDING + 8f
        
        // Header section (store name, address, GSTIN, etc.)
        height += 50f // Store name
        height += 40f // Address lines (estimated 2 lines)
        if (bill.storeInfo.gstin.isNotBlank()) height += SMALL_LINE_HEIGHT
        if (bill.storeInfo.fssaiLicense.isNotBlank()) height += SMALL_LINE_HEIGHT
        if (bill.storeInfo.customerCarePhone.isNotBlank()) height += SMALL_LINE_HEIGHT
        height += 35f // Separator and spacing
        
        // Tax invoice header
        height += LINE_HEIGHT * 2 + SMALL_LINE_HEIGHT + 35f
        
        // Transaction metadata
        height += LINE_HEIGHT * 4 + 35f
        
        // Item table
        height += LINE_HEIGHT // Header
        height += 20f // Separator
        bill.items.forEach { item ->
            // Estimate lines needed for item description
            val descriptionLines = (item.name.length / 30) + 1 // Rough estimate
            height += LINE_HEIGHT * descriptionLines + 10f
        }
        height += 45f // Separator and spacing
        
        // Totals section
        height += LINE_HEIGHT * 5 // Items count, gross, discount, net, paid
        height += 35f // Separator and spacing
        
        // Payment info
        height += LINE_HEIGHT * 2 // Mode and status
        if (bill.paymentInfo.upiId?.isNotBlank() == true) height += LINE_HEIGHT
        if (bill.paymentInfo.cardLast4?.isNotBlank() == true) height += LINE_HEIGHT
        height += 35f // Separator and spacing
        
        // GST section (only if GST details exist)
        if (hasGSTDetails(bill.gstSummary)) {
            height += LINE_HEIGHT + 10f // GST title
            height += LINE_HEIGHT // GST header
            height += 20f // Separator
            height += LINE_HEIGHT * bill.gstSummary.breakup.size // GST rows
            height += 45f // Separator and spacing
        }
        
        // Footer
        if (bill.transactionInfo.paymentReferenceNo?.isNotBlank() == true) height += LINE_HEIGHT
        height += LINE_HEIGHT // Tax invoice no
        height += 35f // Separator and spacing
        height += SMALL_LINE_HEIGHT + 10f // Terms
        height += LINE_HEIGHT * 2 + 20f // Thank you messages
        height += 35f // Separator and spacing
        height += LINE_HEIGHT // Powered by
        
        // Final padding
        height += PADDING
        
        return height.toInt().coerceAtLeast(500) // Minimum height for fixed width
    }
    
    /**
     * Checks if GST details should be rendered
     */
    private fun hasGSTDetails(gstSummary: com.kiranaflow.app.billing.model.GSTSummary): Boolean {
        return gstSummary.breakup.isNotEmpty() && 
               (gstSummary.totalCGST > 0 || gstSummary.totalSGST > 0 || gstSummary.totalCESS > 0)
    }
}
