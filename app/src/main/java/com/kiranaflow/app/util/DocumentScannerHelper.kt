package com.kiranaflow.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around ML Kit's on-device document scanner (Play services backed).
 *
 * Design goals:
 * - additive + non-breaking: if scanner isn't available or fails, callers can fall back
 *   to the existing camera/gallery + OCR pipeline.
 * - keep API surface small: return only scanned page URIs + optional PDF URI.
 */
object DocumentScannerHelper {

    data class ScanOutput(
        val pageUris: List<Uri>,
        val pdfUri: Uri?
    )

    /**
     * Returns an [IntentSender] that the caller should launch via ActivityResult APIs.
     */
    suspend fun getStartScanIntent(
        activity: Activity,
        allowGalleryImport: Boolean = true
    ): IntentSender? {
        val scanner = getClient(activity, allowGalleryImport)
        return scanner.getStartScanIntent(activity).awaitOrNull()
    }

    fun parseResult(data: Intent?): ScanOutput? {
        val intent = data ?: return null
        val res = runCatching { GmsDocumentScanningResult.fromActivityResultIntent(intent) }.getOrNull() ?: return null
        val pages = res.pages?.mapNotNull { it.imageUri }.orEmpty()
        val pdf = res.pdf?.uri
        if (pages.isEmpty() && pdf == null) return null
        return ScanOutput(pageUris = pages, pdfUri = pdf)
    }

    private fun getClient(context: Context, allowGalleryImport: Boolean): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGalleryImport)
            // Prefer higher quality (still on-device); callers decide which output to OCR.
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            // FULL lets user scan multi-page bills; we will OCR first page by default.
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        return GmsDocumentScanning.getClient(options)
    }

    private suspend fun com.google.android.gms.tasks.Task<IntentSender>.awaitOrNull(): IntentSender? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { res -> cont.resume(res) }
            addOnFailureListener { cont.resume(null) }
            addOnCanceledListener { cont.resume(null) }
        }
}


