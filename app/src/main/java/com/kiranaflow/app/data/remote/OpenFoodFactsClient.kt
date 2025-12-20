package com.kiranaflow.app.data.remote

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OffProductInfo(
    val barcode: String,
    val name: String? = null,
    val brand: String? = null,
    val categories: String? = null,
    val imageUrl: String? = null
)

object OpenFoodFactsClient {
    // API v2 is fast and returns JSON with a "product" object.
    fun fetchProduct(barcode: String): OffProductInfo? {
        val clean = barcode.trim()
        if (clean.isBlank()) return null

        val url = URL("https://world.openfoodfacts.org/api/v2/product/$clean.json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "KiranaFlow/1.0 (Android)")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (body.isBlank()) return null

            val root = JSONObject(body)
            val status = root.optInt("status", 0)
            if (status != 1) {
                Log.d("OpenFoodFacts", "Not found for barcode=$clean status=$status")
                return null
            }

            val product = root.optJSONObject("product") ?: return OffProductInfo(barcode = clean)
            OffProductInfo(
                barcode = clean,
                // JSONObject#optString returns a non-null String in most implementations; treat empty/"null" as missing.
                name = product.optString("product_name").takeIf { it.isNotBlank() && it != "null" },
                brand = product.optString("brands").takeIf { it.isNotBlank() && it != "null" },
                categories = product.optString("categories").takeIf { it.isNotBlank() && it != "null" },
                imageUrl = product.optString("image_url").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            Log.e("OpenFoodFacts", "Fetch failed for barcode=$clean", e)
            null
        } finally {
            try {
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}


