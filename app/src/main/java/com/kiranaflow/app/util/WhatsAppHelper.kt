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
        upiId: String
    ): String {
        val shop = shopName.ifBlank { "Kirana Store" }
        val upiLink = buildUpiLink(upiId = upiId, payeeName = shop, amountInr = dueAmountInr).orEmpty()

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
}



