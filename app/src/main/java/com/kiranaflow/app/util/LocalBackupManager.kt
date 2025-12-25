package com.kiranaflow.app.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LocalBackupManager(private val context: Context) {

    companion object {
        private const val FORMAT_VERSION = 1
        private const val META_ENTRY = "meta.json"
        private const val META_JSON = """{"app":"thisizbusiness","formatVersion":$FORMAT_VERSION}"""

        const val MIME_ZIP = "application/zip"
    }

    private fun dbFiles(): List<Pair<String, File>> {
        val main = context.getDatabasePath("kirana_database")
        // Room may use WAL mode, so include -wal and -shm if present.
        return listOf(
            "db/kirana_database" to main,
            "db/kirana_database-wal" to File(main.parentFile, "kirana_database-wal"),
            "db/kirana_database-shm" to File(main.parentFile, "kirana_database-shm"),
        ).filter { (_, f) -> f.exists() }
    }

    private fun dataStoreFiles(): List<Pair<String, File>> {
        // DataStore preference files live under: files/datastore/*.preferences_pb
        val dsDir = File(context.filesDir, "datastore")
        return listOf(
            "datastore/app_prefs.preferences_pb" to File(dsDir, "app_prefs.preferences_pb"),
            "datastore/shop_settings.preferences_pb" to File(dsDir, "shop_settings.preferences_pb"),
        ).filter { (_, f) -> f.exists() }
    }

    suspend fun exportTo(uri: Uri, beforeCopy: (suspend () -> Unit)? = null) = withContext(Dispatchers.IO) {
        beforeCopy?.invoke()

        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(BufferedOutputStream(os)).use { zip ->
                zip.putNextEntry(ZipEntry(META_ENTRY))
                zip.write(META_JSON.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val all = dbFiles() + dataStoreFiles()
                for ((name, file) in all) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open output stream for selected location")
    }

    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "restore_tmp").apply {
            deleteRecursively()
            mkdirs()
        }

        // Unzip into temp
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val outFile = File(tmpDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open input stream for selected file")

        // Validate metadata
        val metaFile = File(tmpDir, META_ENTRY)
        val meta = metaFile.takeIf { it.exists() }?.readText(Charsets.UTF_8).orEmpty()
        val metaOk = meta.contains("thisizbusiness") && meta.contains("formatVersion")
        check(metaOk) { "Not a valid thisizbusiness backup file." }

        // Validate expected folders exist (at least DB)
        val tmpDbDir = File(tmpDir, "db")
        check(tmpDbDir.exists()) { "Backup missing database content." }

        // Replace DB files
        val dbDir = context.getDatabasePath("kirana_database").parentFile!!
        tmpDbDir.listFiles()?.forEach { f ->
            f.copyTo(File(dbDir, f.name), overwrite = true)
        }

        // Replace DataStore files (if present)
        val tmpDsDir = File(tmpDir, "datastore")
        if (tmpDsDir.exists()) {
            val dsDir = File(context.filesDir, "datastore").apply { mkdirs() }
            tmpDsDir.listFiles()?.forEach { f ->
                f.copyTo(File(dsDir, f.name), overwrite = true)
            }
        }
    }
}









