package com.kiranaflow.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OcrUtils {
    suspend fun ocrFromUri(contentResolver: ContentResolver, uri: Uri): String {
        val bitmap = loadBitmap(contentResolver, uri) ?: return ""
        return ocrFromBitmap(bitmap)
    }

    private fun loadBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val type = contentResolver.getType(uri).orEmpty()
        return when {
            type.startsWith("image/") -> decodeImage(contentResolver, uri)
            type == "application/pdf" -> renderFirstPdfPage(contentResolver, uri)
            else -> decodeImage(contentResolver, uri) ?: renderFirstPdfPage(contentResolver, uri)
        }
    }

    private fun decodeImage(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        return runCatching {
            val src = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        }.getOrNull()
    }

    private fun renderFirstPdfPage(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val pfd: ParcelFileDescriptor = runCatching {
            contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: return null

        pfd.use { fd ->
            val renderer = runCatching { PdfRenderer(fd) }.getOrNull() ?: return null
            renderer.use { r ->
                if (r.pageCount <= 0) return null
                val page = r.openPage(0)
                page.use { p ->
                    val width = p.width
                    val height = p.height
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    private suspend fun ocrFromBitmap(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val dev = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

        val latinText = latin.process(image).awaitText()
        val devText = dev.process(image).awaitText()

        return sequenceOf(latinText, devText)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private suspend fun com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text>.awaitText(): String =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { res -> cont.resume(res.text.orEmpty()) }
            addOnFailureListener { cont.resume("") }
        }
}











