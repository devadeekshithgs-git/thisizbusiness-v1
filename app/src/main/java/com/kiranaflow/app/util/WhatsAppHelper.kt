package com.kiranaflow.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object WhatsAppHelper {
    fun normalizeIndianPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("91") && digits.length >= 12 -> digits
            digits.length == 10 -> "91$digits"
            else -> digits
        }
    }

    fun buildUpiLink(upiId: String, payeeName: String, amountInr: Int): String? {
        val cleanUpi = upiId.trim()
        if (cleanUpi.isBlank() || amountInr <= 0) return null
        return try {
            val pn = URLEncoder.encode(payeeName, "UTF-8")
            "upi://pay?pa=$cleanUpi&pn=$pn&am=$amountInr&cu=INR"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Template placeholders:
     * - {name}, {due}, {shop}, {upi_id}, {upi_link}
     */
    fun buildReminderMessage(
        template: String,
        customerName: String,
        dueAmountInr: Int,
        shopName: String,
        upiId: String,
        upiPayeeName: String = ""
    ): String {
        val shop = shopName.ifBlank { "Kirana Store" }
        val payee = upiPayeeName.ifBlank { shop }
        val upiLink = buildUpiLink(upiId = upiId, payeeName = payee, amountInr = dueAmountInr).orEmpty()

        if (template.isBlank()) {
            return buildString {
                append("Hi $customerName, your due amount is ₹$dueAmountInr.\n")
                append("Please pay at the earliest.\n")
                if (upiLink.isNotBlank()) {
                    append("\nPay via UPI: $upiLink\n")
                    append("(UPI ID: ${upiId.trim()})\n")
                }
                append("\n- $shop")
            }
        }

        return template
            .replace("{name}", customerName)
            .replace("{due}", dueAmountInr.toString())
            .replace("{shop}", shop)
            .replace("{upi_id}", upiId.trim())
            .replace("{upi_link}", upiLink)
            .trim()
    }

    fun openWhatsApp(context: Context, phoneWithCountryCode: String, message: String) {
        val encoded = URLEncoder.encode(message, "UTF-8")
        val url = "https://wa.me/$phoneWithCountryCode?text=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(share, "Send reminder"))
        }
    }

    /**
     * Send a PDF directly to the given WhatsApp DM (if WhatsApp is installed).
     *
     * Notes:
     * - phoneWithCountryCode should be digits only, including country code (e.g. "9199xxxxxx").
     * - WhatsApp uses the "jid" extra to open a specific chat.
     */
    fun sendPdfToWhatsAppDm(
        context: Context,
        phoneWithCountryCode: String,
        pdfUri: Uri,
        caption: String? = null,
        chooserTitle: String = "Send bill"
    ) {
        val phone = phoneWithCountryCode.filter { it.isDigit() }
        val jid = "$phone@s.whatsapp.net"

        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            putExtra("jid", jid)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }

        // If WhatsApp isn't available, fall back to generic share chooser.
        val canResolve = waIntent.resolveActivity(context.packageManager) != null
        if (canResolve) {
            context.startActivity(waIntent)
            return
        }

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, chooserTitle))
    }

    /**
     * Build a text-based bill receipt message to send via WhatsApp.
     * @param items List of (itemName, qty, isLoose, unit price, line total)
     * @param totalAmount Total bill amount
     * @param paymentMode CASH, UPI, or CREDIT
     * @param shopName Name of the shop
     * @param customerName Name of the customer
     * @param upiId Optional UPI ID to include for future payments
     * @param template Optional custom template from Settings.
     *
     * Supported placeholders in template:
     * - {shop}, {customer}, {date}, {total}, {payment}, {upi_id}, {upi_link}, {items}
     */
    fun buildBillMessage(
        items: List<BillItem>,
        totalAmount: Double,
        paymentMode: String,
        shopName: String,
        customerName: String,
        upiId: String = "",
        upiPayeeName: String = "",
        template: String = ""
    ): String {
        val shop = shopName.ifBlank { "thisizbusiness" }
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        val dateStr = dateFormat.format(java.util.Date())
        val modeLabel = when (paymentMode) {
            "CASH" -> "Cash"
            "UPI" -> "UPI"
            "CREDIT" -> "Credit (Udhaar)"
            else -> paymentMode
        }

        val payee = upiPayeeName.ifBlank { shop }
        val upiLink = buildUpiLink(
            upiId = upiId,
            payeeName = payee,
            amountInr = totalAmount.toInt().coerceAtLeast(1)
        ).orEmpty()

        val itemsText = buildString {
            items.forEachIndexed { idx, item ->
                val qtyStr = if (item.isLoose) {
                    val kgTxt = String.format("%.3f", item.qty).trimEnd('0').trimEnd('.')
                    "${kgTxt}kg"
                } else {
                    "${item.qty.toInt()} pcs"
                }
                append("${idx + 1}. ${item.name}\n")
                append("   $qtyStr × ₹${item.unitPrice.toInt()} = ₹${item.lineTotal.toInt()}\n")
            }
        }.trimEnd()

        if (template.isNotBlank()) {
            return template
                .replace("{shop}", shop)
                .replace("{customer}", customerName)
                .replace("{date}", dateStr)
                .replace("{total}", totalAmount.toInt().toString())
                .replace("{payment}", modeLabel)
                .replace("{upi_id}", upiId.trim())
                .replace("{upi_link}", upiLink)
                .replace("{items}", itemsText)
                .trim()
        }

        return buildString {
            append("*$shop*\n")
            append("━━━━━━━━━━━━━━━━━━\n")
            append("📋 *BILL RECEIPT*\n")
            append("━━━━━━━━━━━━━━━━━━\n")
            append("📅 $dateStr\n")
            append("👤 $customerName\n")
            append("\n")
            append("*ITEMS:*\n")
            append("─────────────────\n")
            
            items.forEachIndexed { idx, item ->
                val qtyStr = if (item.isLoose) {
                    String.format("%.3f", item.qty).trimEnd('0').trimEnd('.') + "kg"
                } else {
                    "${item.qty.toInt()} pcs"
                }
                append("${idx + 1}. ${item.name}\n")
                append("   $qtyStr × ₹${item.unitPrice.toInt()} = *₹${item.lineTotal.toInt()}*\n")
            }
            
            append("─────────────────\n")
            append("*TOTAL: ₹${totalAmount.toInt()}*\n")
            append("━━━━━━━━━━━━━━━━━━\n")
            
            val modeLabelEmoji = when (paymentMode) {
                "CASH" -> "💵 Cash"
                "UPI" -> "📱 UPI"
                "CREDIT" -> "📝 Credit (Udhaar)"
                else -> paymentMode
            }
            append("Payment: $modeLabelEmoji\n")
            
            if (paymentMode == "CREDIT") {
                append("\n⚠️ _Amount added to your credit._\n")
            }
            
            if (upiId.isNotBlank()) {
                append("\n💳 UPI ID: ${upiId.trim()}\n")
            }
            if (upiLink.isNotBlank()) {
                append("Pay through UPI app: $upiLink\n")
            }
            
            append("\n_Thank you for shopping!_ 🙏\n")
            append("─────────────────\n")
            append("_Powered by thisizbusiness_")
        }
    }

    data class BillItem(
        val name: String,
        val qty: Double,
        val isLoose: Boolean,
        val unitPrice: Double,
        val lineTotal: Double
    )
}




