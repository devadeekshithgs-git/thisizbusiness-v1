package com.kiranaflow.app.util

/**
 * BuildConfig access shim.
 *
 * Some build setups can fail to expose the generated BuildConfig class to Kotlin compilation.
 * This accessor avoids hard compile-time dependency by using reflection.
 */
object BackendConfig {
    val backendBaseUrl: String get() = readString("BACKEND_BASE_URL")
    val backendApiKey: String get() = readString("BACKEND_API_KEY")

    private fun readString(fieldName: String): String {
        return runCatching {
            val cls = Class.forName("com.kiranaflow.app.BuildConfig")
            val f = cls.getField(fieldName)
            (f.get(null) as? String).orEmpty()
        }.getOrElse { "" }
    }
}



