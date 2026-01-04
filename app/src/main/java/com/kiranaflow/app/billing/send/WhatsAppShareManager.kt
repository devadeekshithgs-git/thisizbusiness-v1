package com.kiranaflow.app.billing.send

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.kiranaflow.app.billing.model.BillSnapshot
import com.kiranaflow.app.billing.render.BillBitmapRenderer
import com.kiranaflow.app.billing.render.BillTextFormatter
import com.kiranaflow.app.utils.PhoneFormatter
import java.io.File
import java.io.FileOutputStream

object WhatsAppShareManager {
    
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    private const val DIGITAL_BILLS_DIR = "digital_bills"
    
    /**
     * Sends a digital bill to a specific customer's WhatsApp DM
     * Opens directly to the customer's chat with pre-filled image and caption
     */
    fun sendBillToWhatsApp(
        context: Context,
        bill: BillSnapshot,
        customerPhone: String
    ): WhatsAppShareResult {
        return try {
            Log.d("WhatsAppShare", "Starting WhatsApp share for phone: '$customerPhone'")
            
            // Validate and normalize phone number
            val normalizedPhone = PhoneFormatter.normalizeE164(customerPhone)
            if (normalizedPhone == null) {
                Log.e("WhatsAppShare", "Invalid phone number: $customerPhone")
                return WhatsAppShareResult.Error("Invalid customer phone number")
            }
            
            Log.d("WhatsAppShare", "Normalized phone: '$normalizedPhone'")
            
            // Generate bill image
            val bitmap = BillBitmapRenderer.renderToBitmap(bill)
            
            // Save bitmap to cache
            val imageUri = saveBitmapToCache(context, bitmap, "bill_${bill.transactionInfo.billNo}.png")
            
            // Generate caption text
            val caption = BillTextFormatter.formatWhatsAppCaption(bill)
            
            // Check if WhatsApp is installed first
            val whatsappPackage = getAvailableWhatsAppPackage(context)
            if (whatsappPackage == null) {
                Log.e("WhatsAppShare", "WhatsApp is not installed")
                return WhatsAppShareResult.Error("WhatsApp is not installed. Please install WhatsApp to share bills directly.")
            }
            
            // Try multiple approaches to open WhatsApp directly
            val attempts = listOf(
                { openWhatsAppWithJid(context, normalizedPhone, imageUri, caption, whatsappPackage) },
                { openWhatsAppWithUrl(context, normalizedPhone, imageUri, caption, whatsappPackage) },
                { openWhatsAppWithShareIntent(context, normalizedPhone, imageUri, caption, whatsappPackage) }
            )
            
            for (attempt in attempts) {
                try {
                    attempt()
                    Log.d("WhatsAppShare", "Successfully opened WhatsApp for $normalizedPhone")
                    return WhatsAppShareResult.Success
                } catch (e: Exception) {
                    Log.w("WhatsAppShare", "Attempt failed, trying next approach", e)
                    continue
                }
            }
            
            // If all attempts failed, return error instead of fallback
            Log.e("WhatsAppShare", "All WhatsApp attempts failed")
            WhatsAppShareResult.Error("Failed to open WhatsApp. Please ensure WhatsApp is updated and try again.")
            
        } catch (e: Exception) {
            Log.e("WhatsAppShare", "Failed to send bill via WhatsApp", e)
            WhatsAppShareResult.Error("Failed to send bill: ${e.message}")
        }
    }
    
    /**
     * Gets the available WhatsApp package (regular or business)
     */
    private fun getAvailableWhatsAppPackage(context: Context): String? {
        val packages = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
        
        for (packageName in packages) {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                if (packageInfo != null) {
                    Log.d("WhatsAppShare", "Found WhatsApp package: $packageName")
                    return packageName
                }
            } catch (e: Exception) {
                Log.d("WhatsAppShare", "WhatsApp package $packageName not found", e)
            }
        }
        
        return null
    }
    
    /**
     * Checks if WhatsApp is installed on the device
     */
    private fun isWhatsAppInstalled(context: Context): Boolean {
        return getAvailableWhatsAppPackage(context) != null
    }
    
    /**
     * Opens WhatsApp with JID approach (most reliable for direct chat)
     */
    private fun openWhatsAppWithJid(
        context: Context,
        normalizedPhone: String,
        imageUri: Uri,
        caption: String,
        whatsappPackage: String
    ) {
        val phoneDigits = normalizedPhone.substring(1) // Remove '+' for JID
        val jid = "$phoneDigits@s.whatsapp.net"
        
        Log.d("WhatsAppShare", "Opening WhatsApp with JID: $jid using package: $whatsappPackage")
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra("jid", jid)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(whatsappPackage)
        }
        
        if (intent.resolveActivity(context.packageManager) == null) {
            throw Exception("JID intent cannot be resolved")
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Opens WhatsApp using URL scheme (alternative approach)
     */
    private fun openWhatsAppWithUrl(
        context: Context,
        normalizedPhone: String,
        imageUri: Uri,
        caption: String,
        whatsappPackage: String
    ) {
        val phoneDigits = normalizedPhone.substring(1) // Remove '+'
        
        Log.d("WhatsAppShare", "Opening WhatsApp with URL: wa.me/$phoneDigits using package: $whatsappPackage")
        
        // First open the chat, then share
        val chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phoneDigits"))
        chatIntent.setPackage(whatsappPackage)
        
        if (chatIntent.resolveActivity(context.packageManager) == null) {
            throw Exception("URL intent cannot be resolved")
        }
        
        context.startActivity(chatIntent)
    }
    
    /**
     * Opens WhatsApp using generic share intent (last resort)
     */
    private fun openWhatsAppWithShareIntent(
        context: Context,
        normalizedPhone: String,
        imageUri: Uri,
        caption: String,
        whatsappPackage: String
    ) {
        Log.d("WhatsAppShare", "Opening WhatsApp with share intent using package: $whatsappPackage")
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(whatsappPackage)
        }
        
        if (intent.resolveActivity(context.packageManager) == null) {
            throw Exception("Share intent cannot be resolved")
        }
        
        context.startActivity(intent)
    }
    
        
    /**
     * Saves bitmap to app cache directory for sharing
     */
    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, DIGITAL_BILLS_DIR)
        cacheDir.mkdirs()
        
        val imageFile = File(cacheDir, fileName)
        
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        Log.d("WhatsAppShare", "Saved bill image to: ${imageFile.absolutePath}")
        
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    
    /**
     * Validates if a bill can be shared via WhatsApp
     */
    fun validateBillForWhatsApp(bill: BillSnapshot, customerPhone: String): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Check customer phone
        if (!PhoneFormatter.isValidForWhatsApp(customerPhone)) {
            issues.add("Customer phone number is invalid or missing")
        }
        
        // Check bill data
        if (bill.items.isEmpty()) {
            issues.add("Bill has no items")
        }
        
        if (bill.totals.totalAmountPaid <= 0) {
            issues.add("Bill total amount is invalid")
        }
        
        if (bill.storeInfo.name.isBlank()) {
            issues.add("Store name is missing")
        }
        
        return if (issues.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(issues)
        }
    }
    
    /**
     * Cleans up old bill images from cache
     */
    fun cleanupOldBillImages(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) { // 24 hours
        try {
            val cacheDir = File(context.cacheDir, DIGITAL_BILLS_DIR)
            if (!cacheDir.exists()) return
            
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMs) {
                    if (file.delete()) {
                        Log.d("WhatsAppShare", "Deleted old bill image: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WhatsAppShare", "Failed to cleanup old bill images", e)
        }
    }
}

sealed class WhatsAppShareResult {
    object Success : WhatsAppShareResult()
    data class Error(val message: String) : WhatsAppShareResult()
    object FallbackToChooser : WhatsAppShareResult()
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val issues: List<String>) : ValidationResult()
}
