package com.goldmedal.aillm.ui.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gets a generated picture out of the app: into the device's gallery, or into
 * another app through the system share sheet.
 *
 * Nothing here is a network call. On Android 10+ the picture is written through
 * [MediaStore], which is the supported way to publish an image and needs no
 * storage permission at all; on older releases it goes to the public Pictures
 * directory and the caller has to hold `WRITE_EXTERNAL_STORAGE` (declared with
 * `maxSdkVersion="28"` in the manifest).
 */
object GeneratedImageStore {

    private const val FOLDER = "AILLM"
    private const val MIME_TYPE = "image/png"

    /**
     * Writes [bitmap] to the gallery under `Pictures/AILLM` and returns the new
     * image's URI, or null if the platform refused the write.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val name = "aillm_${System.currentTimeMillis()}.png"
        val isScoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            if (isScoped) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$FOLDER"
                )
                // Keeps the half-written file out of the gallery until we are done.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: false
        }.getOrDefault(false)

        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        if (isScoped) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            runCatching { resolver.update(uri, done, null, null) }
        }
        return uri
    }

    /**
     * A share intent for [bitmap], or null if it could not be staged.
     *
     * The picture is written into the app's cache and handed out through the
     * FileProvider declared in the manifest — sharing must not require the user
     * to also save a copy.
     */
    fun shareIntent(context: Context, bitmap: Bitmap): Intent? {
        val file = runCatching {
            val dir = File(context.cacheDir, SHARE_DIR).apply { if (!exists()) mkdirs() }
            // Old shares are not worth keeping and would grow without bound.
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val target = File(dir, "aillm_share_${System.currentTimeMillis()}.png")
            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            target
        }.getOrNull() ?: return null

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null

        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Where staged shares live; must match `res/xml/file_paths.xml`. */
    const val SHARE_DIR = "shared"
}
