package com.kiranaflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

/**
 * Lightweight, on-device, resettable learning store for bill scanning corrections.
 *
 * We store small maps of common corrections (e.g., OCR item name -> corrected item name).
 * This is NOT ML training; just rule-based reuse of user edits.
 */
private val Context.billLearningDataStore by preferencesDataStore(name = "bill_scan_learning")

class LearningStore(private val context: Context) {
    private object Keys {
        val itemNameMapJson = stringPreferencesKey("item_name_map_json")
        val vendorNameMapJson = stringPreferencesKey("vendor_name_map_json")
    }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    suspend fun reset() {
        context.billLearningDataStore.edit { it.clear() }
    }

    suspend fun recordItemNameCorrection(rawOcrName: String, correctedName: String) {
        val raw = rawOcrName.trim()
        val corrected = correctedName.trim()
        if (raw.isBlank() || corrected.isBlank()) return
        if (raw.equals(corrected, ignoreCase = true)) return

        val key = normalizeKey(raw)
        context.billLearningDataStore.edit { prefs ->
            val cur = loadMap(prefs[Keys.itemNameMapJson])
            val next = cur.toMutableMap().apply { put(key, corrected) }
            prefs[Keys.itemNameMapJson] = gson.toJson(limitSize(next))
        }
    }

    suspend fun recordVendorNameCorrection(rawOcrName: String, correctedName: String) {
        val raw = rawOcrName.trim()
        val corrected = correctedName.trim()
        if (raw.isBlank() || corrected.isBlank()) return
        if (raw.equals(corrected, ignoreCase = true)) return

        val key = normalizeKey(raw)
        context.billLearningDataStore.edit { prefs ->
            val cur = loadMap(prefs[Keys.vendorNameMapJson])
            val next = cur.toMutableMap().apply { put(key, corrected) }
            prefs[Keys.vendorNameMapJson] = gson.toJson(limitSize(next))
        }
    }

    suspend fun applyItemNameCorrection(name: String): String {
        val raw = name.trim()
        if (raw.isBlank()) return raw
        val prefs = context.billLearningDataStore.data.first()
        val map = loadMap(prefs[Keys.itemNameMapJson])
        return map[normalizeKey(raw)] ?: raw
    }

    suspend fun applyVendorNameCorrection(name: String): String {
        val raw = name.trim()
        if (raw.isBlank()) return raw
        val prefs = context.billLearningDataStore.data.first()
        val map = loadMap(prefs[Keys.vendorNameMapJson])
        return map[normalizeKey(raw)] ?: raw
    }

    private fun loadMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(json, mapType) }.getOrElse { emptyMap() }
    }

    private fun normalizeKey(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

    private fun limitSize(m: Map<String, String>, max: Int = 300): Map<String, String> {
        if (m.size <= max) return m
        // Drop oldest-ish deterministically (alphabetical) to keep bounded size.
        return m.entries.sortedBy { it.key }.takeLast(max).associate { it.toPair() }
    }
}



