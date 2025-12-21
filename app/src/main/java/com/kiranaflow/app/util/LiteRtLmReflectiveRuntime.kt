package com.kiranaflow.app.util

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Reflection-based LiteRT-LM runtime wrapper.
 *
 * Why reflection:
 * - `com.google.ai.edge.litertlm:litertlm-android` is compiled with Kotlin metadata 2.2.x.
 * - This app is on Kotlin 1.9.x (Compose compiler 1.5.1), so we cannot put that library on compile classpath.
 * - We depend on it as `runtimeOnly(...)` and call into it reflectively.
 */
class LiteRtLmReflectiveRuntime private constructor(
    context: Context
) : FunctionGemmaRuntime {
    private val appContext = context.applicationContext

    override suspend fun generate(prompt: String): String = withTimeout(18_000L) {
        withContext(Dispatchers.Default) {
            val engine = EngineHolder.getOrCreate(appContext)
            val session = engineCreateSession(engine)
            try {
                sessionGenerateContent(session, prompt)
            } finally {
                runCatching { sessionClose(session) }
            }
        }
    }

    companion object {
        private const val TAG = "LiteRtLmReflective"

        fun tryCreate(context: Context): FunctionGemmaRuntime? {
            // 1) Ensure model asset exists
            val hasAsset = runCatching {
                context.assets.open(FunctionGemmaBillExtractor.DEFAULT_MODEL_ASSET).close()
                true
            }.getOrElse { false }
            if (!hasAsset) return null

            // 2) Ensure runtime classes exist
            val ok = runCatching { Class.forName("com.google.ai.edge.litertlm.Engine") }.isSuccess
            if (!ok) {
                Log.w(TAG, "LiteRT-LM classes not found on runtime classpath. Is the runtimeOnly dependency packaged?")
                return null
            }

            return LiteRtLmReflectiveRuntime(context)
        }
    }

    private object EngineHolder {
        private const val TAG = "LiteRtLmReflective"

        @Volatile
        private var engine: Any? = null

        @Synchronized
        fun getOrCreate(context: Context): Any {
            engine?.let { return it }

            val modelPath = ensureModelOnDisk(context)
            val cacheDir = File(context.cacheDir, "litertlm_cache").apply { mkdirs() }.absolutePath

            val backendCls = Class.forName("com.google.ai.edge.litertlm.Backend")
            val cpu = java.lang.Enum.valueOf(backendCls.asSubclass(Enum::class.java), "CPU")

            val engineConfigCls = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val engineConfig = engineConfigCls.constructors.first { it.parameterTypes.size == 6 }.newInstance(
                /* modelPath */ modelPath,
                /* backend */ cpu,
                /* visionBackend */ cpu,
                /* audioBackend */ cpu,
                /* maxNumTokens */ 2048,
                /* cacheDir */ cacheDir
            )

            val engineCls = Class.forName("com.google.ai.edge.litertlm.Engine")
            val eng = engineCls.getConstructor(engineConfigCls).newInstance(engineConfig)

            // initialize()
            engineCls.getMethod("initialize").invoke(eng)

            engine = eng
            Log.i(TAG, "LiteRT-LM engine initialized (modelPath=$modelPath)")
            return eng
        }

        private fun ensureModelOnDisk(context: Context): String {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val outFile = File(modelsDir, FunctionGemmaBillExtractor.DEFAULT_MODEL_ASSET)
            if (outFile.exists() && outFile.length() > 0L) return outFile.absolutePath

            context.assets.open(FunctionGemmaBillExtractor.DEFAULT_MODEL_ASSET).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outFile.absolutePath
        }
    }

    private fun engineCreateSession(engine: Any): Any {
        val samplerConfigCls = Class.forName("com.google.ai.edge.litertlm.SamplerConfig")
        val samplerConfig = samplerConfigCls.constructors.first { it.parameterTypes.size == 4 }.newInstance(
            /* topK */ 40,
            /* topP */ 0.95,
            /* temperature */ 0.2,
            /* seed */ 1234
        )

        val sessionConfigCls = Class.forName("com.google.ai.edge.litertlm.SessionConfig")
        val sessionConfig = sessionConfigCls.constructors.first { it.parameterTypes.size == 1 }.newInstance(samplerConfig)

        val engineCls = Class.forName("com.google.ai.edge.litertlm.Engine")
        return engineCls.getMethod("createSession", sessionConfigCls).invoke(engine, sessionConfig) as Any
    }

    private fun sessionGenerateContent(session: Any, prompt: String): String {
        // Build InputData.Text(prompt)
        val inputTextCls = Class.forName("com.google.ai.edge.litertlm.InputData\$Text")
        val inputText = inputTextCls.getConstructor(String::class.java).newInstance(prompt)

        val list = java.util.Collections.singletonList(inputText)
        val sessionCls = Class.forName("com.google.ai.edge.litertlm.Session")
        return sessionCls.getMethod("generateContent", List::class.java).invoke(session, list) as String
    }

    private fun sessionClose(session: Any) {
        val sessionCls = Class.forName("com.google.ai.edge.litertlm.Session")
        sessionCls.getMethod("close").invoke(session)
    }
}


