package com.kiranaflow.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object UpiIntentHelper {
    /**
     * Opens a UPI app with a prefilled "Pay" intent using a UPI deep-link.
     *
     * @return true if an Activity launch was attempted, false if inputs are invalid.
     */
    fun openUpiPay(
        context: Context,
        vpa: String,
        payeeName: String,
        amountInr: Int
    ): Boolean {
        val link = WhatsAppHelper.buildUpiLink(
            upiId = vpa.trim(),
            payeeName = payeeName,
            amountInr = amountInr
        ) ?: return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(Intent.createChooser(intent, "Pay via UPI"))
            true
        } catch (_: ActivityNotFoundException) {
            // Fallback: share/copy the UPI link so user can paste it elsewhere.
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(Intent.createChooser(share, "Share UPI link"))
            }.onFailure {
                Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (_: Exception) {
            Toast.makeText(context, "Couldn't open UPI app", Toast.LENGTH_SHORT).show()
            false
        }
    }
}












