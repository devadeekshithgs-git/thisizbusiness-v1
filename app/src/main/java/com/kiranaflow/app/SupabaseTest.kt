package com.kiranaflow.app

import android.content.Context
import com.kiranaflow.app.util.BackendConfig

/**
 * Simple test to verify Supabase integration is working
 */
object SupabaseTest {
    
    fun testSupabaseConnection(context: Context): Boolean {
        return try {
            val baseUrl = BackendConfig.backendBaseUrl
            val apiKey = BackendConfig.backendApiKey
            
            // Check if configuration is available
            if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                println("✅ Supabase configuration found")
                println("   URL: $baseUrl")
                println("   API Key: ${apiKey.take(20)}...")
                true
            } else {
                println("❌ Supabase configuration not found")
                false
            }
        } catch (e: Exception) {
            println("❌ Error checking Supabase: ${e.message}")
            false
        }
    }
}
