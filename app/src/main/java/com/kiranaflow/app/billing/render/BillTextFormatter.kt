package com.kiranaflow.app.billing.render

import com.kiranaflow.app.billing.model.BillSnapshot
import java.text.SimpleDateFormat
import java.util.*

object BillTextFormatter {
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    /**
     * Creates a clean, readable WhatsApp caption from bill data
     * Uses minimal formatting that WhatsApp preserves
     */
    fun formatWhatsAppCaption(bill: BillSnapshot): String {
        return buildString {
            // Store Header
            appendLine(bill.storeInfo.name.uppercase())
            if (bill.storeInfo.address.isNotBlank()) {
                appendLine(bill.storeInfo.address)
            }
            if (bill.storeInfo.gstin.isNotBlank()) {
                appendLine("GSTIN: ${bill.storeInfo.gstin}")
            }
            appendLine()
            
            // Bill Info
            appendLine("*TAX INVOICE*")
            appendLine("Bill No: ${bill.transactionInfo.billNo}")
            appendLine("Date: ${dateFormat.format(bill.transactionInfo.date)}")
            appendLine("Time: ${timeFormat.format(bill.transactionInfo.date)}")
            appendLine("Customer: ${bill.customerInfo.name}")
            if (bill.customerInfo.phone.isNotBlank()) {
                appendLine("Phone: ${bill.customerInfo.phone}")
            }
            appendLine()
            
            // Items
            appendLine("*ITEMS*")
            appendLine("─".repeat(30))
            
            bill.items.forEachIndexed { index, item ->
                val qtyStr = if (item.isLoose) {
                    String.format("%.3f", item.quantity).trimEnd('0').trimEnd('.') + "kg"
                } else {
                    "${item.quantity.toInt()} pcs"
                }
                
                appendLine("${index + 1}. ${item.name}")
                appendLine("   $qtyStr × ₹${formatMoney(item.unitPrice)} = ₹${formatMoney(item.totalAmount)}")
            }
            
            appendLine("─".repeat(30))
            appendLine()
            
            // Totals
            appendLine("*TOTALS*")
            appendLine("Items: ${bill.totals.itemCount}")
            appendLine("Gross Sales: ₹${formatMoney(bill.totals.grossSalesValue)}")
            
            if (bill.totals.totalDiscount > 0) {
                appendLine("Discount: ₹${formatMoney(bill.totals.totalDiscount)}")
            }
            
            appendLine("Net Sales (Incl. GST): ₹${formatMoney(bill.totals.netSalesValue)}")
            appendLine("Total Paid: ₹${formatMoney(bill.totals.totalAmountPaid)}")
            appendLine()
            
            // GST Breakup
            if (bill.gstSummary.breakup.isNotEmpty()) {
                appendLine("*GST BREAKUP*")
                appendLine("─".repeat(30))
                
                bill.gstSummary.breakup.forEach { gst ->
                    appendLine("GST ${gst.gstRate.toInt()}%: ₹${formatMoney(gst.total)}")
                    appendLine("  CGST: ₹${formatMoney(gst.cgstAmount)}")
                    appendLine("  SGST: ₹${formatMoney(gst.sgstAmount)}")
                    if (gst.cessAmount > 0) {
                        appendLine("  CESS: ₹${formatMoney(gst.cessAmount)}")
                    }
                }
                appendLine("─".repeat(30))
                appendLine()
            }
            
            // Payment Info
            appendLine("*PAYMENT*")
            appendLine("Mode: ${bill.paymentInfo.mode}")
            appendLine("Status: ${bill.paymentInfo.status}")
            
            if (!bill.paymentInfo.upiId.isNullOrBlank()) {
                appendLine("UPI ID: ${bill.paymentInfo.upiId}")
            }
            
            appendLine()
            
            // Footer
            appendLine("Thank you for shopping!")
            appendLine("Terms & Conditions Apply")
            appendLine()
            appendLine("Powered by thisizbusiness")
        }
    }
    
    /**
     * Creates a simplified version for quick preview
     */
    fun formatSimpleCaption(bill: BillSnapshot): String {
        return buildString {
            appendLine("*${bill.storeInfo.name}*")
            appendLine("Bill No: ${bill.transactionInfo.billNo}")
            appendLine("Date: ${dateFormat.format(bill.transactionInfo.date)}")
            appendLine()
            
            appendLine("*Items: ${bill.totals.itemCount}*")
            bill.items.take(3).forEachIndexed { index, item ->
                val qtyStr = if (item.isLoose) {
                    String.format("%.3f", item.quantity).trimEnd('0').trimEnd('.') + "kg"
                } else {
                    "${item.quantity.toInt()} pcs"
                }
                appendLine("${index + 1}. ${item.name} x $qtyStr = ₹${formatMoney(item.totalAmount)}")
            }
            
            if (bill.items.size > 3) {
                appendLine("... and ${bill.items.size - 3} more items")
            }
            
            appendLine()
            appendLine("*Total: ₹${formatMoney(bill.totals.totalAmountPaid)}*")
            appendLine("Payment: ${bill.paymentInfo.mode}")
            appendLine()
            appendLine("Thank you for shopping!")
        }
    }
    
    private fun formatMoney(amount: Double): String {
        return String.format("%.2f", amount)
    }
}
