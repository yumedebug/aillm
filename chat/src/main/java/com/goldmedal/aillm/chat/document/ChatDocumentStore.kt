package com.goldmedal.aillm.chat.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Keeps documents attached to a conversation on disk.
 *
 * Same reasoning as [com.goldmedal.aillm.chat.image.ChatImageStore]: the picker
 * hands out a transient URI, so the file is copied into app storage and the
 * message row only remembers its name. Later turns read the file back, which is
 * what lets the user keep asking about a document they attached once.
 */
object ChatDocumentStore {

    private const val DIR_NAME = "user_files/chat"
    private const val MAX_DOCUMENT_BYTES = 8L * 1024 * 1024
    private const val MAX_TEXT_CHARS = 200_000
    private const val BINARY_SAMPLE_BYTES = 1024

    data class Stored(val storedName: String, val displayName: String)

    /** Folder holding conversation documents (Settings → Storage counts it). */
    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    /** Copies a picked document into app storage; null if it is not readable text. */
    fun save(context: Context, uri: Uri, displayName: String?): Stored? = runCatching {
        val name = displayName?.takeIf { it.isNotBlank() } ?: resolveDisplayName(context, uri)
        val target = File(dir(context), "doc_${System.currentTimeMillis()}_${safeName(name)}")
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val copied = input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        if (copied <= 0L || copied > MAX_DOCUMENT_BYTES || !looksLikeText(target)) {
            target.delete()
            return null
        }
        Stored(storedName = target.name, displayName = name)
    }.getOrNull()

    /** Reads a stored document back; null when it is gone or unreadable. */
    fun readText(context: Context, storedName: String?): String? {
        val file = file(context, storedName) ?: return null
        return runCatching { file.readText().take(MAX_TEXT_CHARS) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** Resolves a stored name to an existing file in app storage, if possible. */
    fun file(context: Context, storedName: String?): File? {
        val name = storedName?.trim().orEmpty()
        if (name.isEmpty() || name.contains('/') || name.contains("://")) return null
        return File(dir(context), name).takeIf { it.exists() }
    }

    /** Deletes stored documents, e.g. when their conversation is removed. */
    fun deleteAll(context: Context, storedNames: List<String>) {
        storedNames.forEach { name -> runCatching { file(context, name)?.delete() } }
    }

    /** Name shown in the UI: what the file is actually called. */
    fun resolveDisplayName(context: Context, uri: Uri): String {
        val fromResolver = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromResolver?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "document.txt"
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(60).ifEmpty { "document.txt" }

    /** Cheap binary check: a text attachment should not contain NUL bytes. */
    private fun looksLikeText(file: File): Boolean = runCatching {
        val sample = ByteArray(BINARY_SAMPLE_BYTES)
        val read = file.inputStream().use { stream -> stream.read(sample) }
        if (read <= 0) return@runCatching false
        (0 until read).none { index -> sample[index] == 0.toByte() }
    }.getOrDefault(false)
}
