package com.kiranaflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * User-maintained defaults that map an inventory category -> HSN code.
 *
 * We do NOT try to "auto-classify" a product into HSN from category names because
 * misclassification can cause compliance issues. Instead we offer:
 * - Optional field per product
 * - Optional per-category default (set by the user after checking official sources)
 */
private val Context.categoryHsnDataStore by preferencesDataStore(name = "category_hsn_defaults")

class CategoryHsnStore(private val context: Context) {
    private object Keys {
        val json = stringPreferencesKey("category_hsn_defaults_json")
    }

    val defaults: Flow<Map<String, String>> = context.categoryHsnDataStore.data.map { prefs ->
        val raw = prefs[Keys.json].orEmpty()
        if (raw.isBlank()) return@map emptyMap()

        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@map emptyMap()
        val out = mutableMapOf<String, String>()
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = obj.optString(k).orEmpty().trim()
            if (k.isNotBlank() && v.isNotBlank()) out[k.trim()] = v
        }
        out
    }

    suspend fun setDefault(category: String, hsnCode: String?) {
        val cat = category.trim()
        if (cat.isBlank()) return
        val hsn = hsnCode?.trim()?.ifBlank { null }

        context.categoryHsnDataStore.edit { prefs ->
            val obj = runCatching { JSONObject(prefs[Keys.json].orEmpty()) }.getOrNull() ?: JSONObject()
            if (hsn == null) obj.remove(cat) else obj.put(cat, hsn)
            prefs[Keys.json] = obj.toString()
        }
    }
}


