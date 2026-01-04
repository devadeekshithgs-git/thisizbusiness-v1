package com.kiranaflow.app.common

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.kiranaflow.app.billing.send.ValidationResult
import com.kiranaflow.app.billing.send.WhatsAppShareResult

object ErrorHandler {
    
    private const val TAG = "BillingErrorHandler"
    
    /**
     * Handles WhatsApp sharing errors with user-friendly messages
     */
    fun handleWhatsAppShareError(
        context: Context,
        result: WhatsAppShareResult,
        onError: ((String) -> Unit)? = null
    ) {
        when (result) {
            is WhatsAppShareResult.Success -> {
                // Success - no error handling needed
                Log.d(TAG, "Bill shared successfully via WhatsApp")
            }
            
            is WhatsAppShareResult.Error -> {
                val userMessage = when {
                    result.message.contains("phone", ignoreCase = true) -> 
                        "Customer phone number is invalid. Please check customer details."
                    result.message.contains("not installed", ignoreCase = true) -> 
                        "WhatsApp is not installed. Please install WhatsApp to share bills directly."
                    result.message.contains("updated", ignoreCase = true) -> 
                        "Please update WhatsApp to the latest version and try again."
                    result.message.contains("storage", ignoreCase = true) -> 
                        "Storage error. Please free up some space and try again."
                    else -> 
                        "Failed to send bill via WhatsApp: ${result.message}"
                }
                
                showToast(context, userMessage)
                Log.e(TAG, "WhatsApp share failed: ${result.message}")
                onError?.invoke(result.message)
            }
        }
    }
    
    /**
     * Handles validation errors with detailed feedback
     */
    fun handleValidationError(
        context: Context,
        result: ValidationResult,
        onError: ((List<String>) -> Unit)? = null
    ) {
        when (result) {
            is ValidationResult.Valid -> {
                // Valid - no error handling needed
                Log.d(TAG, "Bill validation passed")
            }
            
            is ValidationResult.Invalid -> {
                val userMessage = if (result.issues.size == 1) {
                    "Cannot send bill: ${result.issues.first()}"
                } else {
                    "Cannot send bill:\n${result.issues.joinToString("\n• ", "• ")}"
                }
                
                showToast(context, userMessage)
                Log.e(TAG, "Bill validation failed: ${result.issues.joinToString(", ")}")
                onError?.invoke(result.issues)
            }
        }
    }
    
    /**
     * Generic error handler for billing operations
     */
    fun handleBillingError(
        context: Context,
        error: Throwable,
        operation: String
    ) {
        val userMessage = when {
            error is SecurityException -> 
                "Permission denied. Please check app permissions."
            error is OutOfMemoryError -> 
                "Device memory is low. Please close other apps and try again."
            error.message?.contains("network", ignoreCase = true) == true -> 
                "Network error. Please check your internet connection."
            error.message?.contains("storage", ignoreCase = true) == true -> 
                "Storage error. Please free up some space and try again."
            else -> 
                "$operation failed. Please try again."
        }
        
        showToast(context, userMessage)
        Log.e(TAG, "$operation failed", error)
    }
    
    /**
     * Shows a toast message safely
     */
    private fun showToast(context: Context, message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast: $message", e)
        }
    }
    
    /**
     * Logs debug information for troubleshooting
     */
    fun logDebug(message: String, data: Any? = null) {
        if (data != null) {
            Log.d(TAG, "$message: $data")
        } else {
            Log.d(TAG, message)
        }
    }
    
    /**
     * Logs performance metrics
     */
    fun logPerformance(operation: String, startTimeMs: Long) {
        val duration = System.currentTimeMillis() - startTimeMs
        Log.d(TAG, "$operation completed in ${duration}ms")
        
        if (duration > 3000) {
            Log.w(TAG, "$operation took longer than expected (${duration}ms)")
        }
    }
}
