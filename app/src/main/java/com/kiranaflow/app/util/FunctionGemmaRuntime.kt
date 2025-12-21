package com.kiranaflow.app.util

import android.content.Context

/**
 * Thin abstraction for an on-device FunctionGemma runtime.
 *
 * Why this exists:
 * - We keep the OCR flow stable by letting the app run even if the runtime/model
 *   isn't available (it will fall back to heuristics).
 */
interface FunctionGemmaRuntime {
    suspend fun generate(prompt: String): String

    companion object {
        /**
         * Returns a runtime if available, else null.
         *
         * Uses LiteRT-LM when the dependency + model asset are present.
         */
        fun tryCreate(context: Context): FunctionGemmaRuntime? {
            // Prefer LiteRT-LM runtime when present (invoked via reflection; runtime is packaged via runtimeOnly).
            return LiteRtLmReflectiveRuntime.tryCreate(context)
        }
    }
}


