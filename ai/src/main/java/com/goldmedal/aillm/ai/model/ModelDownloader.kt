package com.goldmedal.aillm.ai.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads GGUF weights straight into the app's private storage, directly from
 * Hugging Face.
 *
 * - Resumable: a `.part` file is kept and a Range request resumes it, so closing
 *   the app mid-download is safe.
 * - Cancellable: cancelling the calling coroutine aborts the HTTP call but keeps
 *   the partial file.
 * - Complete: models that need a companion file (vision projectors) download
 *   every file, and progress is reported across the whole set.
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun modelsDir(): File = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    /** The main weights file. Its presence is what "installed" means. */
    fun fileFor(spec: ModelSpec): File = File(modelsDir(), spec.fileName)

    fun auxFileFor(spec: ModelSpec, aux: ModelFile): File = File(modelsDir(), aux.fileName)

    fun tempFileFor(name: String): File = File(modelsDir(), "$name.part")

    /** Every file this model needs on disk, main weights first. */
    fun filesFor(spec: ModelSpec): List<File> =
        listOf(fileFor(spec)) + spec.auxiliaryFiles.map { auxFileFor(spec, it) }

    /**
     * Downloads everything [spec] needs. [onProgress] is called with progress
     * across the *whole* set, not per file, so the UI shows one bar.
     */
    suspend fun download(
        spec: ModelSpec,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        val files = mutableListOf<Pair<String, String>>() // name to url
        files += spec.fileName to spec.downloadUrl
        spec.auxiliaryFiles.forEach { files += it.fileName to it.url }

        val knownTotal = files.sumOf { (name, _) ->
            when {
                name == spec.fileName -> spec.sizeBytes
                else -> spec.auxiliaryFiles.firstOrNull { it.fileName == name }?.sizeBytes ?: 0L
            }
        }

        val results = mutableListOf<File>()
        var completedBytes = 0L

        try {
            for ((name, url) in files) {
                val target = File(modelsDir(), name)
                if (target.exists() && target.length() > 0L) {
                    completedBytes += target.length()
                    results += target
                    val total = maxOf(knownTotal, completedBytes)
                    onProgress(progressOf(completedBytes, total), completedBytes, total)
                    continue
                }

                val fileResult = downloadFile(
                    name = name,
                    url = url,
                    onBytes = { fileBytes ->
                        val total = maxOf(knownTotal, completedBytes + fileBytes)
                        onProgress(progressOf(completedBytes + fileBytes, total), completedBytes + fileBytes, total)
                    }
                )
                val file = fileResult.getOrElse { return@withContext Result.failure(it) }
                completedBytes += file.length()
                results += file
            }
            Result.success(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadFile(
        name: String,
        url: String,
        onBytes: (Long) -> Unit
    ): Result<File> {
        val target = File(modelsDir(), name)
        val temp = tempFileFor(name)
        return try {
            var existing = if (temp.exists()) temp.length() else 0L
            val requestBuilder = Request.Builder().url(url)
            if (existing > 0L) requestBuilder.header("Range", "bytes=$existing-")

            val call = client.newCall(requestBuilder.build())
            coroutineContext[Job]?.invokeOnCompletion { call.cancel() }

            call.execute().use { response ->
                if (response.code == 416) {
                    existing = 0L
                } else if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP ${response.code} for $name"))
                }

                val body = response.body ?: return Result.failure(IOException("Empty response for $name"))
                val append = response.code == 206 && existing > 0L
                if (!append) existing = 0L

                var downloaded = existing
                FileOutputStream(temp, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onBytes(downloaded)
                        }
                    }
                    output.flush()
                }
            }

            if (!temp.exists() || temp.length() == 0L) {
                return Result.failure(IOException("Download produced no data for $name"))
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Result.success(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun progressOf(done: Long, total: Long): Float =
        if (total > 0L) (done.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
}
