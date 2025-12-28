package com.kiranaflow.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Makes product images stable by copying any picked/cropped Uri into app-private storage and
 * returning a FileProvider-backed content Uri string.
 *
 * Why: Gallery Uris often have temporary grant permissions, and uCrop outputs are in cache. Both
 * can cause failures/crashes right after Save or later when loading thumbnails.
 */
object ProductImageStore {
    private const val TAG = "ProductImageStore"

    fun persistIfNeeded(context: Context, uriString: String?): String? {
        val trimmed = uriString?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null

        // Already persisted by us? Keep as-is.
        // We store under filesDir/product_images and serve via FileProvider.
        val ourAuthority = "${context.packageName}.fileprovider"
        val isOurProvider = uri.scheme == "content" && uri.authority == ourAuthority
        if (isOurProvider && (uri.encodedPath?.contains("product_images") == true)) {
            return trimmed
        }

        return runCatching {
            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val outFile = File(dir, "product_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

            val input = when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> uri.path?.let { FileInputStream(File(it)) }
                else -> context.contentResolver.openInputStream(uri)
            } ?: return@runCatching null

            input.use { ins ->
                FileOutputStream(outFile).use { outs ->
                    ins.copyTo(outs)
                    outs.flush()
                }
            }

            FileProvider.getUriForFile(context, ourAuthority, outFile).toString()
        }.onFailure { t ->
            Log.e(TAG, "persistIfNeeded failed for uri=$trimmed", t)
        }.getOrNull()
    }
}








