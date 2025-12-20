package com.kiranaflow.app.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Real backend wiring:
 * - Sends a versioned SyncEnvelope (as JSON) to the backend.
 * - Uses opId as idempotency key.
 *
 * Expected backend contract (suggested):
 * - Receives POST {baseUrl}/sync/apply
 * - Reads envelope fields + body
 * - Applies to remote DB idempotently using opId
 */
class HttpRemoteApi(
    private val baseUrl: String,
    private val apiKey: String?,
    private val client: OkHttpClient = OkHttpClient()
) : RemoteApi {
    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun apply(envelope: SyncEnvelope, request: RemoteRequestPreview): RemoteResult {
        if (baseUrl.isBlank()) return RemoteResult(false, "Backend not configured (baseUrl empty)")

        // For Supabase Edge Functions, set baseUrl to the FULL function URL, e.g.
        // https://<project-ref>.functions.supabase.co/sync-apply
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
                // Supabase Edge Functions commonly accept:
                // - apikey: <anon key>
                // - Authorization: Bearer <anon key OR user access token>
                if (!k.isNullOrBlank()) {
                    header("apikey", k)
                    header("Authorization", "Bearer $k")
                }
            }
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
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


