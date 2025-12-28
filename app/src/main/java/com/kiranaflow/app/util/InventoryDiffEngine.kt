package com.kiranaflow.app.util

import com.kiranaflow.app.data.local.ChangeType
import com.kiranaflow.app.data.local.ItemEntity

/**
 * Diff engine to match scanned bill line-items to existing inventory items.
 *
 * We keep this deterministic + conservative:
 * - prefer exact name match (case-insensitive)
 * - then normalized token match
 * - then a lightweight similarity score (no heavy ML)
 */
object InventoryDiffEngine {

    data class MatchResult(
        val matchedItem: ItemEntity?,
        val confidence: Float
    )

    fun matchItem(
        scannedName: String,
        existingItems: List<ItemEntity>
    ): MatchResult {
        val name = scannedName.trim()
        if (name.isBlank()) return MatchResult(null, 0f)

        // 1) Exact (case-insensitive) match.
        existingItems.firstOrNull { !it.isDeleted && it.name.equals(name, ignoreCase = true) }?.let {
            return MatchResult(it, 1.0f)
        }

        // 2) Normalized match.
        val norm = normalize(name)
        val byNorm = existingItems
            .asSequence()
            .filter { !it.isDeleted }
            .map { it to normalize(it.name) }
            .firstOrNull { (_, n) -> n.isNotBlank() && n == norm }
            ?.first
        if (byNorm != null) return MatchResult(byNorm, 0.85f)

        // 3) Similarity (token overlap + prefix).
        val best = existingItems
            .asSequence()
            .filter { !it.isDeleted }
            .map { it to similarity(norm, normalize(it.name)) }
            .maxByOrNull { it.second }

        val (item, score) = best ?: return MatchResult(null, 0f)
        val conf = score.coerceIn(0f, 0.8f)
        return if (conf >= 0.55f) MatchResult(item, conf) else MatchResult(null, conf)
    }

    fun computeChangeType(
        existing: ItemEntity,
        scannedQty: Int,
        scannedCostPrice: Double?
    ): ChangeType {
        val qtyChanged = scannedQty > 0
        val priceChanged = scannedCostPrice != null && scannedCostPrice > 0.0 && scannedCostPrice != existing.costPrice
        return when {
            qtyChanged && priceChanged -> ChangeType.QTY_AND_PRICE
            qtyChanged -> ChangeType.QTY_ONLY
            priceChanged -> ChangeType.PRICE_ONLY
            else -> ChangeType.QTY_ONLY
        }
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .filterNot { it in STOPWORDS }
            .joinToString(" ")
            .trim()

    private fun similarity(a: String, b: String): Float {
        if (a.isBlank() || b.isBlank()) return 0f
        if (a == b) return 1f

        val aTokens = a.split(" ").filter { it.isNotBlank() }.toSet()
        val bTokens = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0f

        val inter = aTokens.intersect(bTokens).size.toFloat()
        val union = aTokens.union(bTokens).size.toFloat().coerceAtLeast(1f)
        val jacc = inter / union

        val prefixBoost = when {
            a.length >= 6 && b.startsWith(a.take(6)) -> 0.15f
            b.length >= 6 && a.startsWith(b.take(6)) -> 0.15f
            else -> 0f
        }
        return (jacc + prefixBoost).coerceIn(0f, 1f)
    }

    private val STOPWORDS = setOf(
        "pcs", "pc", "nos", "no", "kg", "g", "gm", "gms", "ml", "ltr", "l",
        "pack", "pkt", "mrp", "rs", "inr"
    )
}



