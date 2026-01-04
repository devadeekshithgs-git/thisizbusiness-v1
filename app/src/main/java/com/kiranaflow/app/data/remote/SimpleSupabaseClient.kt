package com.kiranaflow.app.data.remote

import com.kiranaflow.app.util.BackendConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

/**
 * Simple Supabase client using OkHttp directly
 */
object SimpleSupabaseClient {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val baseUrl = "${BackendConfig.backendBaseUrl}/rest/v1/"
    private val apiKey = BackendConfig.backendApiKey
    
    /**
     * Get all records from a table
     */
    suspend fun getAll(table: String, deviceId: String): List<Map<String, Any>> {
        val url = "${baseUrl}$table?device_id=eq.$deviceId"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        
        return executeRequest(request)
    }
    
    /**
     * Insert a record into a table
     */
    suspend fun insert(table: String, data: Any): Map<String, Any> {
        val url = "${baseUrl}$table"
        val jsonBody = json.encodeToString(data)
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Prefer", "return=representation")
            .post(body)
            .build()
        
        return executeRequest(request).first()
    }
    
    /**
     * Update a record in a table
     */
    suspend fun update(table: String, id: String, data: Any): Map<String, Any> {
        val url = "${baseUrl}$table?id=eq.$id"
        val jsonBody = json.encodeToString(data)
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Prefer", "return=representation")
            .patch(body)
            .build()
        
        return executeRequest(request).first()
    }
    
    /**
     * Delete a record from a table
     */
    suspend fun delete(table: String, id: String) {
        val url = "${baseUrl}$table?id=eq.$id"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .delete()
            .build()
        
        executeRequest(request)
    }
    
    /**
     * Execute HTTP request and parse response
     */
    private suspend fun executeRequest(request: Request): List<Map<String, Any>> {
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string() ?: "[]"
                json.decodeFromString<List<Map<String, Any>>>(responseBody)
            }
        } catch (e: Exception) {
            println("Supabase request failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * Check if client is configured
     */
    fun isConfigured(): Boolean {
        return BackendConfig.backendBaseUrl.isNotBlank() && 
               BackendConfig.backendApiKey.isNotBlank()
    }
}

/**
 * Extension properties for table access
 */
val SimpleSupabaseClient.kfItems get() = "kf_items"
val SimpleSupabaseClient.kfParties get() = "kf_parties"
val SimpleSupabaseClient.kfTransactions get() = "kf_transactions"
val SimpleSupabaseClient.kfTransactionItems get() = "kf_transaction_items"
val SimpleSupabaseClient.kfReminders get() = "kf_reminders"
val SimpleSupabaseClient.kfSyncOps get() = "kf_sync_ops"
