package com.kiranaflow.app.utils

import android.util.Log

object PhoneFormatter {
    
    /**
     * Normalizes a phone number to E.164 format with Indian country code.
     * Ensures WhatsApp opens the correct customer chat.
     * 
     * @param phone Raw phone number from customer data
     * @return Normalized phone number in format "+91XXXXXXXXXX" or null if invalid
     */
    fun normalizeE164(phone: String): String? {
        if (phone.isBlank()) {
            Log.w("PhoneFormatter", "Empty phone number provided")
            return null
        }
        
        // Extract only digits
        val digits = phone.filter { it.isDigit() }
        
        val normalized = when {
            // Already has country code (91) and correct length
            digits.startsWith("91") && digits.length == 12 -> {
                "+$digits"
            }
            // 10-digit Indian number - add country code
            digits.length == 10 -> {
                "+91$digits"
            }
            // Number with country code but wrong length
            digits.startsWith("91") -> {
                Log.w("PhoneFormatter", "Invalid Indian number format: $phone (digits: $digits)")
                null
            }
            // Other lengths - log but don't process
            else -> {
                Log.w("PhoneFormatter", "Unsupported phone number format: $phone (digits: $digits)")
                null
            }
        }
        
        Log.d("PhoneFormatter", "Normalized: '$phone' -> '$normalized'")
        return normalized
    }
    
    /**
     * Validates if a phone number can be used for WhatsApp sharing
     */
    fun isValidForWhatsApp(phone: String): Boolean {
        return normalizeE164(phone) != null
    }
    
    /**
     * Extracts just the digits for WhatsApp JID format (without +)
     */
    fun toWhatsAppJid(phone: String): String? {
        val normalized = normalizeE164(phone)
        return normalized?.substring(1) // Remove '+' for JID format
    }
}
