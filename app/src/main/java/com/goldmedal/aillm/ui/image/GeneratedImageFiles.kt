package com.goldmedal.aillm.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Where generated pictures actually live: the app's private storage, next to
 * the downloaded models, with the metadata that makes them findable kept in
 * Room.
 *
 * Nothing here is visible to other apps. Publishing a picture to the device
 * gallery is a separate, explicit action ([GeneratedImageStore]); this store is
 * what the in-app gallery reads, so looking back at what was generated never
 * requires a storage permission.
 */
object GeneratedImageFiles {

    private const val DIR_NAME = "generated"

    /** Folder holding generated pictures. */
    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Writes [bitmap] as a PNG and returns its file name, or null if the write
     * failed. A failed write deletes its own half-file so the gallery can never
     * point at a broken image.
     */
    fun write(context: Context, bitmap: Bitmap, stamp: Long = System.currentTimeMillis()): String? {
        val fileName = "img_$stamp.png"
        val target = File(dir(context), fileName)
        val written = runCatching {
            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }.getOrDefault(false)

        if (!written) {
            runCatching { target.delete() }
            return null
        }
        return fileName
    }

    /** The file for [fileName], or null when it is missing or empty. */
    fun file(context: Context, fileName: String): File? =
        File(dir(context), fileName).takeIf { it.isFile && it.length() > 0L }

    fun delete(context: Context, fileName: String) {
        runCatching { File(dir(context), fileName).delete() }
    }

    /**
     * Decodes [fileName] to a Bitmap, or null if the file is gone.
     *
     * With [maxSize] above zero the picture is downsampled to roughly that
     * many pixels on its longest edge — the grid draws thumbnails, and a full
     * 512px decode per cell is memory the screen does not need to spend.
     */
    fun loadBitmap(context: Context, fileName: String, maxSize: Int = 0): Bitmap? {
        val path = file(context, fileName)?.absolutePath ?: return null
        if (maxSize <= 0) return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxSize) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    /** Total bytes the generated pictures occupy, for the storage screen. */
    fun sizeBytes(context: Context): Long =
        runCatching { dir(context).listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)
}
