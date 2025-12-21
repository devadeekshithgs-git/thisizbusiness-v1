package com.kiranaflow.app.util.gst

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object GstFileExporter {
    private const val TAG = "GstFileExporter"

    fun saveTextToDownloads(context: Context, displayName: String, mimeType: String, text: String): Uri? {
        return saveBytesToDownloads(
            context = context,
            displayName = displayName,
            mimeType = mimeType,
            bytes = text.toByteArray(Charsets.UTF_8)
        )
    }

    fun saveBytesToDownloads(context: Context, displayName: String, mimeType: String, bytes: ByteArray): Uri? {
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/thisizbusiness")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null

            resolver.openOutputStream(uri, "w")?.use { os ->
                os.write(bytes)
                os.flush()
            } ?: run {
                // Cleanup failed write
                runCatching { resolver.delete(uri, null, null) }
                return@runCatching null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }

            uri
        }.onFailure { e ->
            Log.e(TAG, "Failed to save file to Downloads: $displayName", e)
        }.getOrNull()
    }

    fun share(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}



