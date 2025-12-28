package com.kiranaflow.app.util

import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device entity extraction helper for vendor-bill OCR text.
 *
 * We use this defensively: best-effort hints for vendor/invoice fields.
 * Parsing/commit logic still requires user review.
 */
object EntityExtractionHelper {

    data class ExtractedEntities(
        val phones: List<String> = emptyList(),
        val addresses: List<String> = emptyList(),
        val datesEpochMillis: List<Long> = emptyList(),
        val moneyAmounts: List<Double> = emptyList()
    )

    suspend fun extract(ocrText: String): ExtractedEntities {
        val text = ocrText.trim()
        if (text.isBlank()) return ExtractedEntities()

        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )

        // Ensure model is available (downloads on-demand via Play services; still on-device).
        val ready = extractor.downloadModelIfNeeded().awaitBool()
        if (!ready) return ExtractedEntities()

        val ann = extractor.annotate(text).awaitOrNull().orEmpty()

        val phones = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        val dates = mutableListOf<Long>()
        val money = mutableListOf<Double>()

        for (a in ann) {
            for (e in a.entities) {
                when (e.type) {
                    Entity.TYPE_PHONE -> {
                        // This library exposes phone/address as raw annotated text span (no typed entity).
                        val v = a.annotatedText.orEmpty().filter { it.isDigit() }
                        if (v.isBlank()) continue
                        val normalized = if (v.length > 10) v.takeLast(10) else v
                        phones.add(normalized)
                    }
                    Entity.TYPE_ADDRESS -> {
                        val v = a.annotatedText.orEmpty().trim()
                        if (v.isNotBlank()) addresses.add(v)
                    }
                    Entity.TYPE_DATE_TIME -> {
                        val ts = runCatching { e.asDateTimeEntity()?.timestampMillis }.getOrNull() ?: continue
                        dates.add(ts)
                    }
                    Entity.TYPE_MONEY -> {
                        val me = runCatching { e.asMoneyEntity() }.getOrNull() ?: continue
                        val num = me.integerPart.toDouble() + (me.fractionalPart.toDouble() / 100.0)
                        if (num > 0.0) money.add(num)
                    }
                }
            }
        }

        return ExtractedEntities(
            phones = phones.distinct(),
            addresses = addresses.distinct(),
            datesEpochMillis = dates.distinct(),
            moneyAmounts = money.distinct()
        )
    }

    private suspend fun com.google.android.gms.tasks.Task<Void>.awaitBool(): Boolean =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(true) }
            addOnFailureListener { cont.resume(false) }
            addOnCanceledListener { cont.resume(false) }
        }

    private suspend fun com.google.android.gms.tasks.Task<List<EntityAnnotation>>.awaitOrNull(): List<EntityAnnotation>? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resume(null) }
            addOnCanceledListener { cont.resume(null) }
        }
}


