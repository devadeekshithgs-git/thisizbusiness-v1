package com.kiranaflow.app.ui.screens.billing

import androidx.compose.runtime.Immutable
import java.util.UUID

enum class SessionStatus {
    ACTIVE,
    ON_HOLD
}

@Immutable
data class BillingSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val customerId: Int? = null,
    val customerName: String? = null,
    val items: List<BoxCartItem> = emptyList(),
    val status: SessionStatus = SessionStatus.ACTIVE
)
