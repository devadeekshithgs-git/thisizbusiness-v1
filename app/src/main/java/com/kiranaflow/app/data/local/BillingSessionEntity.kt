package com.kiranaflow.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "billing_sessions")
data class BillingSessionEntity(
    @PrimaryKey val sessionId: String,
    val createdAt: Long,
    val customerId: Int?,
    val customerName: String?,
    val itemsJson: String,
    val status: String
)
