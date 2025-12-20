package com.kiranaflow.app.sync

import org.json.JSONObject

data class SyncEnvelope(
    val apiVersion: Int = 1,
    val deviceId: String,
    val opId: String,
    val sentAtMillis: Long,
    val entityType: String,
    val entityId: String?,
    val op: String,
    val body: JSONObject?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("apiVersion", apiVersion)
            put("deviceId", deviceId)
            put("opId", opId)
            put("sentAtMillis", sentAtMillis)
            put("entityType", entityType)
            put("entityId", entityId)
            put("op", op)
            put("body", body)
        }
    }
}







