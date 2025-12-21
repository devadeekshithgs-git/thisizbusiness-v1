package com.kiranaflow.app.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * FunctionGemma-based (function-calling) extractor.
 *
 * This file intentionally contains:
 * - the function schema (so the model returns stable JSON)
 * - robust parsing/validation/sanitization
 *
 * The actual on-device runtime hookup can be added behind [tryRunModel] without
 * changing call sites.
 */
object FunctionGemmaBillExtractor {
     private const val TAG = "FunctionGemmaBillExtractor"

     /**
      * Expected model asset filename (place under `app/src/main/assets/`).
      *
      * You can keep it as-is and just drop the model file with this name,
      * or change it when wiring the runtime.
      */
     const val DEFAULT_MODEL_ASSET = "functiongemma_270m.task"

    /**
     * Try extracting a structured bill from OCR text using FunctionGemma.
     *
     * Returns null when:
     * - runtime is unavailable
     * - model output is unusable
     * - extraction confidence is too low
     */
    suspend fun tryExtract(context: Context, ocrText: String): BillOcrParser.ParsedBill? {
        val prompt = buildPrompt(ocrText)
        val raw = tryRunModel(context, prompt) ?: return null
        val json = extractFirstJsonObject(raw) ?: return null
        return parseAndSanitize(json)
    }

    /**
     * Runtime hook.
     *
     * TODO: Integrate LiteRT-LM runtime + FunctionGemma model here.
     * Keep this method returning null until the runtime is added, so the app
     * safely falls back to heuristics.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun tryRunModel(context: Context, prompt: String): String? {
         // If no runtime dependency is present, we cannot run the model yet.
         // Keep fallback behavior intact, but be explicit in logs so setup is obvious.
         val runtime = FunctionGemmaRuntime.tryCreate(context) ?: run {
             Log.w(TAG, "No FunctionGemma runtime configured. Falling back to heuristic parser.")
             return null
         }

         // If the app doesn't ship a model asset yet, also fall back.
         // (Runtime implementations should load DEFAULT_MODEL_ASSET.)
         val hasAsset = runCatching { context.assets.open(DEFAULT_MODEL_ASSET).close(); true }.getOrElse { false }
         if (!hasAsset) {
             Log.w(TAG, "Missing model asset: $DEFAULT_MODEL_ASSET under app/src/main/assets/. Falling back.")
             return null
         }

         return runCatching { runtime.generate(prompt) }.getOrNull()
    }

    private fun buildPrompt(ocrText: String): String {
        // Minimal, robust prompt: ask for a single tool-call JSON object.
        // (We keep the schema in-app so we can validate the output.)
        val schema = toolSchemaJson().toString()
        return """
You are extracting structured data from an OCR'd vendor bill.

Return ONLY a JSON object matching this schema:
$schema

Rules:
- Keep vendor.name as printed on the bill. Do not invent.
- vendor.phone must be 10 digits if present; else null.
- vendor.gstNumber must match GSTIN pattern if present; else null.
- Each item must have: name (string), qty (number), unit (PCS|KG|G), unitPrice (number or null), total (number or null)
- Prefer line items from the bill table; ignore subtotal/tax/roundoff lines.
- If unsure about a field, set it to null rather than guessing.

OCR TEXT:
${ocrText.trim()}
""".trimIndent()
    }

    private fun toolSchemaJson(): JSONObject {
        val vendor = JSONObject()
            .put("name", JSONObject().put("type", "string").put("nullable", true))
            .put("phone", JSONObject().put("type", "string").put("nullable", true))
            .put("gstNumber", JSONObject().put("type", "string").put("nullable", true))

        val item = JSONObject()
            .put("name", JSONObject().put("type", "string"))
            .put("qty", JSONObject().put("type", "number"))
            .put("unit", JSONObject().put("type", "string"))
            .put("unitPrice", JSONObject().put("type", "number").put("nullable", true))
            .put("total", JSONObject().put("type", "number").put("nullable", true))

        return JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("vendor", JSONObject()
                    .put("type", "object")
                    .put("properties", vendor)
                )
                .put("items", JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject()
                        .put("type", "object")
                        .put("properties", item)
                    )
                )
            )
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun parseAndSanitize(jsonObjectString: String): BillOcrParser.ParsedBill? {
        val root = runCatching { JSONObject(jsonObjectString) }.getOrNull() ?: return null
        val vendorObj = root.optJSONObject("vendor") ?: JSONObject()
        val itemsArr = root.optJSONArray("items") ?: JSONArray()

        val vendor = BillOcrParser.ParsedVendor(
            name = vendorObj.optString("name").trim().ifBlank { null },
            gstNumber = vendorObj.optString("gstNumber").trim().ifBlank { null },
            phone = vendorObj.optString("phone").filter { it.isDigit() }.takeLast(10).ifBlank { null }
        )

        val items = buildList {
            for (i in 0 until itemsArr.length()) {
                val o = itemsArr.optJSONObject(i) ?: continue
                // Avoid `continue` inside inline lambdas (experimental Kotlin feature).
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                val qtyRaw = o.optDouble("qty", Double.NaN)
                if (qtyRaw.isNaN() || qtyRaw.isInfinite() || qtyRaw <= 0.0) continue
                val qtyInt = kotlin.math.round(qtyRaw).toInt().coerceAtLeast(1)

                val unit = o.optString("unit").trim().uppercase().let {
                    when (it) {
                        "KG" -> "KG"
                        "G", "GRAM", "GRAMS" -> "G"
                        else -> "PCS"
                    }
                }

                val unitPrice = o.optDouble("unitPrice", Double.NaN).let { if (it.isNaN() || it <= 0.0) null else it }
                val total = o.optDouble("total", Double.NaN).let { if (it.isNaN() || it <= 0.0) null else it }

                // Require at least unitPrice or total; otherwise it's usually noise.
                if (unitPrice == null && total == null) continue

                add(
                    BillOcrParser.ParsedBillItem(
                        name = name,
                        qty = qtyInt,
                        qtyRaw = qtyRaw,
                        unit = unit,
                        unitPrice = unitPrice,
                        total = total ?: (unitPrice?.times(qtyInt)),
                        rawLine = name
                    )
                )
            }
        }

        return BillOcrParser.ParsedBill(vendor = vendor, items = items)
    }
}


