package com.kiranaflow.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real backend wiring - OPTIMIZED FOR SPEED:
 * - Uses singleton OkHttpClient with connection pooling
 * - Supports batch sync (multiple ops in one request)
 * - Parallel processing of individual ops when batching not available
 * - Uses opId as idempotency key.
 *
 * Expected backend contract:
 * - Single op: POST {baseUrl}/sync/apply with SyncEnvelope
 * - Batch ops: POST {baseUrl}/sync/apply-batch with { ops: SyncEnvelope[] }
 */
class HttpRemoteApi(
    private val baseUrl: String,
    private val apiKey: String?
) : RemoteApi {
    
    companion object {
        private val json = "application/json; charset=utf-8".toMediaType()
        
        // Singleton OkHttpClient with optimized settings for fast sync
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                // Fast connection timeouts
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // Aggressive connection pooling
                .connectionPool(ConnectionPool(
                    maxIdleConnections = 5,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                ))
                // Enable HTTP/2 for multiplexing
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                // Retry on connection failure
                .retryOnConnectionFailure(true)
                .build()
        }
        
        // Maximum concurrent requests for parallel sync
        private const val MAX_PARALLEL_REQUESTS = 5
        
        // Batch size for batch sync endpoint
        private const val BATCH_SIZE = 20
    }

    override suspend fun apply(envelope: SyncEnvelope, request: RemoteRequestPreview): RemoteResult {
        if (baseUrl.isBlank()) return RemoteResult(false, "Backend not configured (baseUrl empty)")
        return withContext(Dispatchers.IO) {
            applySingle(envelope, request)
        }
    }
    
    /**
     * Batch apply multiple sync operations in a single HTTP request.
     * Falls back to parallel individual requests if batch endpoint fails.
     */
    suspend fun applyBatch(
        envelopes: List<Pair<SyncEnvelope, RemoteRequestPreview>>
    ): List<RemoteResult> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext envelopes.map { 
                RemoteResult(false, "Backend not configured (baseUrl empty)") 
            }
        }
        
        if (envelopes.isEmpty()) return@withContext emptyList()
        
        // Try batch endpoint first
        val batchResult = tryBatchApply(envelopes)
        if (batchResult != null) return@withContext batchResult
        
        // Fallback: parallel individual requests
        applyParallel(envelopes)
    }
    
    /**
     * Try to send all ops in a single batch request.
     * Returns null if batch endpoint is not available.
     */
    private fun tryBatchApply(
        envelopes: List<Pair<SyncEnvelope, RemoteRequestPreview>>
    ): List<RemoteResult>? {
        val batchUrl = "${baseUrl.trim().removeSuffix("/")}-batch"
        
        // Build batch payload
        val opsArray = JSONArray()
        envelopes.forEach { (envelope, preview) ->
            opsArray.put(JSONObject().apply {
                put("envelope", envelope.toJson())
                put("preview", JSONObject().apply {
                    put("method", preview.method)
                    put("path", preview.path)
                })
            })
        }
        
        val batchBody = JSONObject().apply {
            put("ops", opsArray)
        }
        
        val req = Request.Builder()
            .url(batchUrl)
            .post(batchBody.toString().toRequestBody(json))
            .header("Content-Type", "application/json")
            .header("X-Batch-Count", envelopes.size.toString())
            .apply {
                val k = apiKey?.trim()
                if (!k.isNullOrBlank()) {
                    header("apikey", k)
                    header("Authorization", "Bearer $k")
                }
            }
            .build()
        
        return try {
            sharedClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // Batch endpoint not available or failed, return null to fallback
                    return null
                }
                
                val responseBody = resp.body?.string() ?: return null
                val json = JSONObject(responseBody)
                val results = json.optJSONArray("results") ?: return null
                
                // Parse individual results
                (0 until results.length()).map { i ->
                    val result = results.getJSONObject(i)
                    RemoteResult(
                        ok = result.optBoolean("ok", false),
                        message = result.optString("message", "")
                    )
                }
            }
        } catch (e: Exception) {
            // Batch endpoint failed, return null to fallback
            null
        }
    }
    
    /**
     * Apply ops in parallel with limited concurrency.
     */
    private suspend fun applyParallel(
        envelopes: List<Pair<SyncEnvelope, RemoteRequestPreview>>
    ): List<RemoteResult> = coroutineScope {
        // Process in chunks to limit concurrent connections
        envelopes.chunked(MAX_PARALLEL_REQUESTS).flatMap { chunk ->
            chunk.map { (envelope, preview) ->
                async(Dispatchers.IO) {
                    applySingle(envelope, preview)
                }
            }.awaitAll()
        }
    }
    
    /**
     * Apply a single sync operation.
     */
    private fun applySingle(envelope: SyncEnvelope, request: RemoteRequestPreview): RemoteResult {
        val url = baseUrl.trim()
        val bodyStr = envelope.toJson().toString()

        val req = Request.Builder()
            .url(url)
            .post(bodyStr.toRequestBody(json))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", envelope.opId)
            .header("X-Device-Id", envelope.deviceId)
            .header("X-Preview-Method", request.method)
            .header("X-Preview-Path", request.path)
            .apply {
                val k = apiKey?.trim()
                if (!k.isNullOrBlank()) {
                    header("apikey", k)
                    header("Authorization", "Bearer $k")
                }
            }
            .build()

        return try {
            sharedClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string()
                if (resp.isSuccessful) {
                    RemoteResult(true, "HTTP ${resp.code} ${resp.message}")
                } else {
                    RemoteResult(false, "HTTP ${resp.code} ${resp.message}${if (text.isNullOrBlank()) "" else ": $text"}")
                }
            }
        } catch (e: Exception) {
            RemoteResult(false, "HTTP error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}


