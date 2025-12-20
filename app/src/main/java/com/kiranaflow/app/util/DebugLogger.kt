package com.kiranaflow.app.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

object DebugLogger {
    private val logFile = File("c:\\Users\\devad\\Downloads\\kiranaflow\\android\\.cursor\\debug.log")
    
    fun log(
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        hypothesisId: String? = null,
        sessionId: String = "debug-session",
        runId: String = "run1"
    ) {
        try {
            // Ensure parent directory exists
            val parent = logFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            
            val dataJson = data.entries.joinToString(", ") { 
                "\"${it.key}\":${formatValue(it.value)}" 
            }
            val json = """{"timestamp":${System.currentTimeMillis()},"location":"$location","message":"$message","data":{$dataJson},"hypothesisId":${if (hypothesisId != null) "\"$hypothesisId\"" else "null"},"sessionId":"$sessionId","runId":"$runId"}"""
            
            // Use append mode with auto-close to prevent file locking
            FileWriter(logFile, true).use { writer ->
                PrintWriter(writer, true).use { printer ->
                    printer.println(json)
                    printer.flush()
                }
            }
        } catch (e: Exception) {
            // Silently fail - don't crash app if logging fails
            // Could optionally log to Android Log here for debugging
            android.util.Log.e("DebugLogger", "Failed to write log: ${e.message}", e)
        }
    }
    
    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${value.replace("\"", "\\\"")}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> "[${value.joinToString(",") { formatValue(it) }}]"
            else -> "\"$value\""
        }
    }
}

