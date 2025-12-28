package com.kiranaflow.app.util

import com.kiranaflow.app.data.local.ItemEntity

/**
 * Central, reusable stock validation helpers.
 *
 * IMPORTANT:
 * - Read-only: does not mutate inventory.
 * - Caller must source [ItemEntity] from local Room/cache (offline-safe).
 */
object StockValidator {
    data class ValidationResult(
        val canAdd: Boolean,
        val availableStock: Double,
        val message: String? = null
    )

    data class CheckoutValidation(
        val ok: Boolean,
        val offendingItemIds: Set<Int> = emptySet(),
        val message: String? = null
    )

    /**
     * Validate whether adding [qtyToAdd] on top of [currentQtyInBill] is allowed.
     *
     * - Non-loose items: stock is [ItemEntity.stock] (PCS, integer)
     * - Loose items: stock is [ItemEntity.stockKg] (KG, double)
     */
    fun canAddToBill(item: ItemEntity, currentQtyInBill: Double, qtyToAdd: Double): ValidationResult {
        val add = qtyToAdd.coerceAtLeast(0.0)
        val current = currentQtyInBill.coerceAtLeast(0.0)
        val requestedTotal = current + add

        val available = if (item.isLoose) item.stockKg.coerceAtLeast(0.0) else item.stock.toDouble().coerceAtLeast(0.0)

        if (available <= 0.0) {
            return ValidationResult(
                canAdd = false,
                availableStock = 0.0,
                message = "Out of Stock"
            )
        }

        if (requestedTotal > available) {
            val unitsAvailable = if (item.isLoose) available else available.toInt().toDouble()
            val x = if (item.isLoose) formatLoose(unitsAvailable) else unitsAvailable.toInt().toString()
            return ValidationResult(
                canAdd = false,
                availableStock = available,
                message = "Only $x units available"
            )
        }

        return ValidationResult(
            canAdd = true,
            availableStock = available,
            message = null
        )
    }

    /**
     * Validate checkout for all line items, using a fresher stock snapshot if available.
     *
     * [freshStock] should be the latest local DB/cache snapshot keyed by itemId.
     */
    fun validateCheckout(
        billItems: List<Pair<ItemEntity, Double>>,
        freshStock: Map<Int, ItemEntity>
    ): CheckoutValidation {
        if (billItems.isEmpty()) return CheckoutValidation(ok = true)

        val offending = mutableSetOf<Int>()
        for ((itemSnapshot, qty) in billItems) {
            val currentItem = freshStock[itemSnapshot.id] ?: itemSnapshot
            val res = canAddToBill(currentItem, currentQtyInBill = 0.0, qtyToAdd = qty)
            if (!res.canAdd) offending += itemSnapshot.id
        }

        return if (offending.isEmpty()) {
            CheckoutValidation(ok = true)
        } else {
            CheckoutValidation(
                ok = false,
                offendingItemIds = offending,
                message = "Stock unavailable. Please review bill items."
            )
        }
    }

    private fun formatLoose(kg: Double): String {
        // Keep it readable; avoid long decimals in the fixed message.
        val txt = String.format("%.3f", kg).trimEnd('0').trimEnd('.')
        return if (txt.isBlank()) "0" else txt
    }
}


