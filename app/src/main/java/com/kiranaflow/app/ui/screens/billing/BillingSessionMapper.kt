package com.kiranaflow.app.ui.screens.billing

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kiranaflow.app.data.local.BillingSessionEntity

private val gson = Gson()
private val boxCartItemListType = object : TypeToken<List<BoxCartItem>>() {}.type

fun BillingSession.toEntity(): BillingSessionEntity {
    return BillingSessionEntity(
        sessionId = sessionId,
        createdAt = createdAt,
        customerId = customerId,
        customerName = customerName,
        itemsJson = gson.toJson(items, boxCartItemListType),
        status = status.name
    )
}

fun BillingSessionEntity.toModel(): BillingSession {
    val items: List<BoxCartItem> = gson.fromJson(itemsJson, boxCartItemListType) ?: emptyList()
    return BillingSession(
        sessionId = sessionId,
        createdAt = createdAt,
        customerId = customerId,
        customerName = customerName,
        items = items,
        status = SessionStatus.valueOf(status)
    )
}
