package com.kiranaflow.app.data.remote

import android.content.Context
import com.kiranaflow.app.util.BackendConfig

/**
 * Supabase client configuration for Direct Integration
 * 
 * This class provides a singleton instance of the Supabase client
 * configured with the project credentials from BackendConfig.
 * 
 * Features:
 * - Auto-configuration from build properties
 * - Offline-first support with local caching
 * - Real-time subscriptions (simplified)
 * - Authentication handling
 */
object SupabaseClient {
    
    /**
     * Initialize the Supabase client with project credentials
     * Should be called once during app startup (e.g., in Application class)
     */
    fun initialize(context: Context) {
        // Using SimpleSupabaseClient for now
        if (SimpleSupabaseClient.isConfigured()) {
            println("Supabase client initialized successfully")
        } else {
            println("Warning: Supabase client not properly configured")
        }
    }
    
    /**
     * Check if the client is properly initialized and configured
     */
    fun isConfigured(): Boolean {
        return SimpleSupabaseClient.isConfigured()
    }
    
    // Convenience accessors for table names
    val kfItems get() = SimpleSupabaseClient.kfItems
    val kfParties get() = SimpleSupabaseClient.kfParties
    val kfTransactions get() = SimpleSupabaseClient.kfTransactions
    val kfTransactionItems get() = SimpleSupabaseClient.kfTransactionItems
    val kfReminders get() = SimpleSupabaseClient.kfReminders
    val kfSyncOps get() = SimpleSupabaseClient.kfSyncOps
}
