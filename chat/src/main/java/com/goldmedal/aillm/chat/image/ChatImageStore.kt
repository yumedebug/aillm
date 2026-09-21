package com.goldmedal.aillm.chat.image

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps images shared in a conversation on disk.
 *
 * The picker only hands out a transient `content://` URI, so a picture would be
 * gone the moment the app restarts. Instead we copy it into app-private storage
 * and remember the file *name* in the message row, which is what lets the same
 * image stay part of the conversation on every later turn.
 */
object ChatImageStore {

    private const val DIR_NAME = "images"
    private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

    /** Folder holding conversation images (also what Settings → Storage reports). */
    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    /** Copies a picked image into app storage; returns the stored name. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val target = File(dir(context), "img_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val copied = input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        if (copied <= 0L || copied > MAX_IMAGE_BYTES) {
            target.delete()
            return null
        }
        target.name
    }.getOrNull()

    /** Resolves a stored value to an existing file in app storage, if possible. */
    fun file(context: Context, stored: String?): File? {
        val name = stored?.trim().orEmpty()
        if (name.isEmpty() || name.contains("://")) return null
        val candidate = if (name.startsWith("/")) File(name) else File(dir(context), name)
        return candidate.takeIf { it.exists() }
    }

    /**
     * Coil model for a stored value: a [File] for images we saved, or the raw
     * string for legacy rows that still hold a `content://` URI.
     */
    fun model(context: Context, stored: String?): Any? {
        val name = stored?.trim().orEmpty()
        if (name.isEmpty()) return null
        if (name.contains("://")) return name
        return file(context, name)
    }

    /** Deletes stored images, e.g. when their conversation is removed. */
    fun deleteAll(context: Context, stored: List<String>) {
        stored.forEach { name -> runCatching { file(context, name)?.delete() } }
    }
}
