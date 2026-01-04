package com.kiranaflow.app

import android.app.Application
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.remote.SimpleRealtimeManager
import com.kiranaflow.app.data.remote.SupabaseClient
import com.kiranaflow.app.util.ConnectivityMonitor
import com.kiranaflow.app.util.DeviceIdProvider
import com.kiranaflow.app.SupabaseTest

/**
 * Application class for KiranaFlow
 * 
 * Initializes global dependencies and services:
 * - Supabase client for Direct Integration
 * - Network connectivity monitoring
 * - Real-time subscriptions
 * - Device ID management
 * - Other singletons and global configurations
 */
class KiranaApplication : Application() {
    
    // Lazy initialization of database
    val database by lazy { KiranaDatabase.getDatabase(this) }
    
    // Device ID for this installation
    val deviceId: String
        get() = DeviceIdProvider.getDeviceId(this)
    
    // Real-time manager for Supabase subscriptions
    private var realtimeManager: SimpleRealtimeManager? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Test Supabase connection
        if (SupabaseTest.testSupabaseConnection(this)) {
            println("✅ Supabase integration is ready!")
        }
        
        // Initialize Supabase client for Direct Integration
        try {
            SupabaseClient.initialize(this)
            if (SupabaseClient.isConfigured()) {
                println("✅ Supabase client initialized successfully")
                
                // Initialize real-time subscriptions
                initializeRealtimeManager()
            } else {
                println("⚠️ Warning: Supabase client not properly configured")
            }
        } catch (e: Exception) {
            println("❌ Error initializing Supabase client: ${e.message}")
        }
        
        // Initialize connectivity monitor for offline-first support
        ConnectivityMonitor.initialize(this)
    }
    
    /**
     * Initialize real-time subscription manager
     */
    private fun initializeRealtimeManager() {
        try {
            realtimeManager = SimpleRealtimeManager(
                itemDao = database.itemDao(),
                partyDao = database.partyDao(),
                transactionDao = database.transactionDao(),
                deviceId = deviceId
            )
            realtimeManager?.initialize()
            println("Real-time manager initialized")
        } catch (e: Exception) {
            println("Error initializing real-time manager: ${e.message}")
        }
    }
    
    /**
     * Get the real-time manager instance
     */
    fun getRealtimeManager(): SimpleRealtimeManager? = realtimeManager
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up real-time subscriptions
        realtimeManager?.cleanup()
    }
}
